import sbt._

import scala.xml._

import Keys._
import AndroidKeys._
import AndroidHelpers._

import sbinary.DefaultProtocol.StringFormat

object AndroidBase {

  private def apklibSourcesTask =
    (extractApkLibDependencies, streams, skipApkLibDependencies) map {
    (projectLibs, s, skip) => {
      if (!skip && !projectLibs.isEmpty) {
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
    (update in Compile, sourceManaged, managedJavaPath, resourceManaged, streams, skipApkLibDependencies) map {
    (updateReport, srcManaged, javaManaged, resManaged, s, skip) => {

      val apklibs: Seq[File] = if (skip) {
        Seq.empty
      } else {
        updateReport.matching(artifactFilter(`type` = "apklib"))
      }

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

    Seq(aPath.absolutePath, "package", "--auto-add-overlay", "-m",
      "--custom-package", mPackage,
      "-M", mPath.head.absolutePath,
      "-S", resPath.absolutePath,
      "-I", jPath.absolutePath,
      "-J", javaPath.absolutePath) ++
      libraryResPathArgs ++
      libraryAssetPathArgs !

    apklibs.foreach { (lib) =>
      Seq(aPath.absolutePath, "package", "--auto-add-overlay", "-m",
        "--custom-package", lib.pkgName,
        "-M", mPath.head.absolutePath,
        "-S", resPath.absolutePath,
        "-I", jPath.absolutePath,
        "-J", javaPath.absolutePath,
        "--non-constant-id") ++
      libraryResPathArgs ++
      libraryAssetPathArgs !
    }

    javaPath ** "R.java" get
  }

  private def aidlGenerateTask =
    (sourceDirectories, idlPath, managedJavaPath, javaSource, streams) map {
    (sDirs, idPath, javaPath, jSource, s) =>
    val aidlPaths = sDirs.map(_ ** "*.aidl").reduceLeft(_ +++ _).get
    if (aidlPaths.isEmpty) {
      s.log.debug("no AIDL files found, skipping")
      Nil
    } else {
      val processor = aidlPaths.map { ap =>
        idPath.absolutePath ::
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
       fullClasspath.filterNot( cp =>
         cp.get(moduleID.key).map( module => preinstalledModules.exists( m =>
               m.organization == module.organization &&
               m.name == module.name)
         ).getOrElse(false)
       ).map(_.data) --- proguardExclude get
    },

    makeManagedJavaPath <<= directory(managedJavaPath),

    apklibSources <<= apklibSourcesTask,
    aaptGenerate <<= aaptGenerateTask,
    aaptGenerate <<= aaptGenerate dependsOn makeManagedJavaPath,
    aidlGenerate <<= aidlGenerateTask,

    unmanagedJars in Compile <++= (libraryJarPath) map (_.map(Attributed.blank(_))),

    sourceGenerators in Compile <+= (apklibSources, aaptGenerate, aidlGenerate) map (_ ++ _ ++ _),

    resourceDirectories <+= (mainAssetsPath),

    cachePasswords := false,
    skipApkLibDependencies := false
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
