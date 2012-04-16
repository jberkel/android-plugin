import sbt._

import scala.xml._

import Keys._
import AndroidKeys._
import AndroidHelpers._

import sbinary.DefaultProtocol.StringFormat

object AndroidBase {
  def getNativeTarget(parent: File, name: String, abi: String) = {
    val extension = "-" + abi + ".so"
    if (name endsWith extension) {
      val stripped = name.substring(0, name indexOf '-') + ".so"
      val target = new File(abi) / stripped
      Some(parent / target.toString)
    } else None
  }

  def copyNativeLibrariesTask =
    (streams, managedNativePath, dependencyClasspath in Compile) map {
    (s, natives, deps) => {
      val sos = (deps.map(_.data)).filter(_.name endsWith ".so")
      var copied = Seq.empty[File]
      for (so <- sos)
        getNativeTarget(natives, so.name, "armeabi") orElse getNativeTarget(natives, so.name, "armeabi-v7a") map {
          target =>
            target.getParentFile.mkdirs
            IO.copyFile(so, target)
            copied +:= target
            s.log.debug("copied native:   " + target.toString)
        }
      /* clean up stale native libraries */
      for (path <- IO.listFiles(natives / "armeabi") ++ IO.listFiles(natives / "armeabi-v7a")) {
        s.log.debug("checking native: " + path.toString)
        if (path.name.endsWith(".so") && !copied.contains(path)) {
          IO.delete(path)
          s.log.debug("deleted native:  " + path.toString)
        }
      }
    }
  }

  private def apklibSourcesTask =
    (extractApkLibDependencies, streams) map {
    (projectLibs, s) => {
      if (!projectLibs.isEmpty) {
        s.log.debug("generating source files from apklibs")
        val xs = for (
          l <- projectLibs;
          f <- l.sources
        ) yield f

        s.log.info("generated " + xs.size + " source files from " + projectLibs.size + " apklibs")
        xs
      } else Seq.empty
    }
  }

  private def apklibDependenciesTask =
    (update in Compile, sourceManaged, managedJavaPath, resourceManaged, streams) map {
    (updateReport, srcManaged, javaManaged, resManaged, s) => {

      val apklibs = updateReport.matching(artifactFilter(`type` = "apklib"))

      apklibs map  { apklib =>
        s.log.info("extracting apklib " + apklib.name)
        val dest = srcManaged / ".." / apklib.base

        val unzipped = IO.unzip(apklib, dest)
        def moveContents(fromDir: File, toDir: File) = {
          toDir.mkdirs()
          val pairs = for (
            file <- unzipped;
            rel <- IO.relativize(fromDir, file)
          ) yield (file, toDir / rel)
          IO.move(pairs)
          pairs map { case (_,t) => t }
        }
        val sources = moveContents(dest / "src", javaManaged)

        val manifest = dest / "AndroidManifest.xml"
        val pkgName = XML.loadFile(manifest).attribute("package").get.head.text
        LibraryProject(
          pkgName,
          manifest,
          sources,
          Some(dest / "res") filter { _.exists },
          Some(dest / "assets") filter { _.exists }
        )
      }
    }
    }

  private def aaptGenerateTask =
    (manifestPackage, aaptPath, manifestPath, mainResPath, jarPath, managedJavaPath, extractApkLibDependencies, streams) map {
    (mPackage, aPath, mPath, resPath, jPath, javaPath, apklibs, s) =>

    val libraryResPathArgs = for (
      lib <- apklibs;
      d <- lib.resDir.toSeq;
      arg <- Seq("-S", d.absolutePath)
    ) yield arg

    val libraryAssetPathArgs = for (
      lib <- apklibs;
      d <- lib.assetsDir.toSeq;
      arg <- Seq("-A", d.absolutePath)
    ) yield arg

    def runAapt(`package`: String, args: String*) {
      val aapt = Seq(aPath.absolutePath, "package", "--auto-add-overlay", "-m",
        "--custom-package", `package`,
        "-M", mPath.head.absolutePath,
        "-S", resPath.absolutePath,
        "-I", jPath.absolutePath,
        "-J", javaPath.absolutePath) ++
        args ++
        libraryResPathArgs ++
        libraryAssetPathArgs
      if (aapt.run(false).exitValue != 0) sys.error("error generating resources")
    }
    runAapt(mPackage)
    apklibs.foreach(lib => runAapt(lib.pkgName, "--non-constant-id"))
    javaPath ** "R.java" get
  }

  private def aidlGenerateTask =
    (sourceDirectories, idlPath, platformPath, managedJavaPath, javaSource, streams) map {
    (sDirs, idPath, platformPath, javaPath, jSource, s) =>
    val aidlPaths = sDirs.map(_ ** "*.aidl").reduceLeft(_ +++ _).get
    if (aidlPaths.isEmpty) {
      s.log.debug("no AIDL files found, skipping")
      Nil
    } else {
      val processor = aidlPaths.map { ap =>
        idPath.absolutePath ::
          "-p" + (platformPath / "framework.aidl").absolutePath ::
          "-o" + javaPath.absolutePath ::
          "-I" + jSource.absolutePath ::
          ap.absolutePath :: Nil
      }.foldLeft(None.asInstanceOf[Option[ProcessBuilder]]) { (f, s) =>
        f match {
          case None => Some(s)
          case Some(first) => Some(first #&& s)
        }
      }.get
      s.log.debug("generating aidl "+processor)
      processor !

      val rPath = javaPath ** "R.java"
      javaPath ** "*.java" --- (rPath) get
    }
  }

  def findPath() = (manifestPath) map { p =>
      manifest(p.head).attribute("package").getOrElse(sys.error("package not defined")).text
  }

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    platformPath <<= (sdkPath, platformName) (_ / "platforms" / _),

    packageApkName <<= (artifact, versionName) map ((a, v) => String.format("%s-%s.apk", a.name, v)),
    packageApkPath <<= (target, packageApkName) map (_ / _),
    manifestPath <<= (sourceDirectory, manifestName) map((s,m) => Seq(s / m)),

    manifestPackage <<= findPath,
    manifestPackageName <<= findPath storeAs manifestPackageName triggeredBy manifestPath,

    minSdkVersion <<= (manifestPath, manifestSchema) map ( (p,s) => usesSdk(p.head, s, "minSdkVersion")),
    maxSdkVersion <<= (manifestPath, manifestSchema) map ( (p,s) => usesSdk(p.head, s, "maxSdkVersion")),
    versionName <<= (manifestPath, manifestSchema, version) map ((p, schema, version) =>
        manifest(p.head).attribute(schema, "versionName").map(_.text).getOrElse(version)
    ),
    nativeLibrariesPath <<= (sourceDirectory) (_ / "libs"),
    mainAssetsPath <<= (sourceDirectory, assetsDirectoryName) (_ / _),
    mainResPath <<= (sourceDirectory, resDirectoryName) (_ / _) map (x=> x),
    managedJavaPath <<= (sourceManaged in Compile) (_ / "java"),
    managedScalaPath <<= (sourceManaged in Compile) ( _ / "scala"),
    managedNativePath <<= (sourceManaged in Compile) (_ / "native_libs"),

    extractApkLibDependencies <<= apklibDependenciesTask,

    managedSourceDirectories in Compile <<= (managedJavaPath, managedScalaPath) (Seq(_, _)),

    classesMinJarPath <<= (target, classesMinJarName) (_ / _),
    classesDexPath <<= (target, classesDexName) (_ / _),
    resourcesApkPath <<= (target, resourcesApkName) (_ / _),
    useProguard := true,
    proguardOptimizations := Seq.empty,

    jarPath <<= (platformPath, jarName) (_ / _),
    libraryJarPath <<= (jarPath (_ get)),

    proguardOption := "",
    proguardExclude <<= (libraryJarPath, classDirectory, resourceDirectory) map {
        (libPath, classDirectory, resourceDirectory) =>
          libPath :+ classDirectory :+ resourceDirectory
    },
    proguardInJars <<= (fullClasspath, proguardExclude, preinstalledModules) map {
      (fullClasspath, proguardExclude, preinstalledModules) =>
       // remove preinstalled jars
       fullClasspath.filterNot( cp =>
         cp.get(moduleID.key).map( module => preinstalledModules.exists( m =>
               m.organization == module.organization &&
               m.name == module.name)
         ).getOrElse(false)
       // only include jar files
       ).filter( cp =>
          cp.get(artifact.key).map(artifact => artifact.`type` == "jar").getOrElse(true)
       ).map(_.data) --- proguardExclude get
    },

    makeManagedJavaPath <<= directory(managedJavaPath),

    copyNativeLibraries <<= copyNativeLibrariesTask,
    classpathTypes in Compile := Set("jar", "so"),

    apklibSources <<= apklibSourcesTask,
    aaptGenerate <<= aaptGenerateTask,
    aaptGenerate <<= aaptGenerate dependsOn makeManagedJavaPath,
    aidlGenerate <<= aidlGenerateTask,

    unmanagedJars in Compile <++= (libraryJarPath) map (_.map(Attributed.blank(_))),

    sourceGenerators in Compile <+= (apklibSources, aaptGenerate, aidlGenerate) map (_ ++ _ ++ _),

    resourceDirectories <+= (mainAssetsPath),

    cachePasswords := false
  ) ++ Seq (
    // Handle the delegates for android settings
    classDirectory <<= (classDirectory in Compile),
    sourceDirectory <<= (sourceDirectory in Compile),
    sourceDirectories <<= (sourceDirectories in Compile),
    resourceDirectory <<= (resourceDirectory in Compile),
    resourceDirectories <<= (resourceDirectories in Compile),
    javaSource <<= (javaSource in Compile),
    managedClasspath <<= (managedClasspath in Runtime),
    fullClasspath <<= (fullClasspath in Runtime)
  ))
}
