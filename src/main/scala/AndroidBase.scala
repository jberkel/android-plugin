package org.scalasbt.androidplugin

import sbt._

import scala.xml._

import Keys._
import AndroidPlugin._
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
    (streams, managedNativePath, dependencyClasspath) map {
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

  private def apklibPackageTask =
    (manifestPath, mainResPath, mainAssetsPath, javaSource, scalaSource, packageApkLibPath, streams) map {
      (manPath, rPath, aPath, jPath, sPath, apklib, s) =>
      s.log.info("packaging apklib")
      val mapping =
        (PathFinder(manPath)            x flat) ++
        (PathFinder(jPath) ** "*.java"  x rebase(jPath, "src")) ++
        (PathFinder(sPath) ** "*.scala" x rebase(sPath, "src")) ++
        ((PathFinder(rPath) ***)        x rebase(rPath, "res")) ++
        ((PathFinder(aPath) ***)        x rebase(aPath, "assets"))
      IO.jar(mapping, apklib, new java.util.jar.Manifest)
      apklib
    }

  private def apklibDependenciesTask =
    (update, sourceManaged, managedJavaPath, resourceManaged, streams) map {
    (updateReport, srcManaged, javaManaged, resManaged, s) => {

      val allApklibs = updateReport.matching(artifactFilter(`type` = "apklib"))
      val providedApklibs = updateReport.matching(configurationFilter(name = "provided"))
      val apklibs = allApklibs --- providedApklibs get

      apklibs map { apklib =>
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
    (manifestPackage, aaptPath, manifestPath, mainResPath, jarPath, managedJavaPath, extractApkLibDependencies, streams, useDebug) map {
    (mPackage, aPath, mPath, resPath, jPath, javaPath, apklibs, s, useDebug) =>

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
      s.log.info("Running AAPT for package " + `package`)
      s.log.info("  Resource path: " + resPath.absolutePath)
      s.log.info("  Manifest path: " + mPath.head.absolutePath)

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

    def createBuildConfig(`package`: String) = {
      var path = javaPath
      `package`.split('.').foreach { path /= _ }
      path.mkdirs
      val buildConfig = path / "BuildConfig.java"
      IO.write(buildConfig, """
        package %s;
        public final class BuildConfig {
          public static final boolean DEBUG = %s;
        }""".format(`package`, useDebug))
      buildConfig
    }

    (javaPath ** "R.java" get) ++
      Seq(createBuildConfig(mPackage)) ++
      apklibs.map(lib => createBuildConfig(lib.pkgName))
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

  def isPreinstalled(f: Attributed[java.io.File], preinstalled: Seq[ModuleID]): Boolean = {
    f.get(moduleID.key) match {
      case Some(m) => preinstalled exists { pm =>
        pm.organization == m.organization &&
        pm.name == m.name
      }

      case None => false
    }
  }

  def isArtifact(f: Attributed[java.io.File], classpathTypes: Set[String]): Boolean = {
    f.get(artifact.key) match {
      case Some(t) => (classpathTypes - "so") contains t.`type`
      case None => true
    }
  }

  lazy val settings: Seq[Setting[_]] = (Seq (
    platformPath <<= (sdkPath, platformName) (_ / "platforms" / _),

    classesMinJarName <<= (artifact, configuration, version) (
      (a, c, v) => "classes-%s-%s-%s.min.jar".format(a.name, c.name, v) ),

    classesDexName <<= (artifact, configuration, version) (
      (a, c, v) => "classes-%s-%s-%s.dex".format(a.name, c.name, v) ),

    resourcesApkName <<= (artifact, configuration, version) (
      (a, c, v) => "resources-%s-%s-%s.apk".format(a.name, c.name, v) ),

    packageApkName <<= (artifact, configuration, versionName) map (
      (a, c, v) => "%s-%s-%s.apk".format(a.name, c.name, v) ),

    packageApkLibName <<= (artifact, configuration, versionName) map (
      (a, c, v) => "%s-%s-%s.apklib".format(a.name, c.name, v) ),

    packageApkPath <<= (target, packageApkName) map (_ / _),
    packageApkLibPath <<= (target, packageApkLibName) map (_ / _),

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

    managedJavaPath <<= (sourceManaged) (_ / "java"),
    managedScalaPath <<= (sourceManaged) ( _ / "scala"),
    managedNativePath <<= (sourceManaged) (_ / "native_libs"),

    extractApkLibDependencies <<= apklibDependenciesTask,
    apklibPackage <<= apklibPackageTask,

    proguardOutputPath <<= (target, classesMinJarName) (_ / _),

    dxOutputPath <<= (target, classesDexName) (_ / _),

    dxInputs <<= (proguard, proguardInJars, proguardLibraryJars, classDirectory) map (
      (proguard, proguardInJars, proguardLibraryJars, classDirectory) => proguard match {
        case Some(f) => Seq(f)
        case None => proguardInJars --- proguardLibraryJars get
      }
    ),

    dxPredex <<= (managedClasspath) map (_.files),

    resourcesApkPath <<= (target, resourcesApkName) (_ / _),
    proguardOptimizations := Seq.empty,

    buildConfigDebug := false,

    jarPath <<= (platformPath, jarName) (_ / _),
    libraryJarPath <<= (jarPath (_ get)),

    proguardOption := "",

    // JARs to be treater as library JARs by Proguard
    proguardLibraryJars <<= (update, usePreloadedScala, scalaInstance, libraryJarPath, internalDependencyClasspath) map {
      (updateReport, usePreloadedScala, scalaInstance, libraryJarPath, internalDependencyClasspath) => (

        // Provided JARs are library JARs by default
        updateReport.select(Set("provided")) ++

        // Add the Scala library if usePreloadedScala is false
        (usePreloadedScala match {
          case true => Seq(scalaInstance.libraryJar)
          case false => Seq.empty
        }) ++

        // The Android library is a library JAR
        libraryJarPath ++

        // Add the internal dependency classpath
        // (Corresponds to the source classes inherited from other projects)
        internalDependencyClasspath.files
      )
    },

    // All the input JARs, including the library ones
    proguardInJars <<= (fullClasspath, preinstalledModules, classpathTypes, resourceDirectory) map {
      (fullClasspath, preinstalledModules, classpathTypes, resourceDirectory) =>

      fullClasspath filter { f =>
        !isPreinstalled(f, preinstalledModules) &&
        isArtifact(f, classpathTypes)
      } map (_.data) filterNot(_ == resourceDirectory)
    },

    makeManagedJavaPath <<= directory(managedJavaPath),

    copyNativeLibraries <<= copyNativeLibrariesTask,

    apklibSources <<= apklibSourcesTask,
    aaptGenerate <<= aaptGenerateTask,
    aaptGenerate <<= aaptGenerate dependsOn makeManagedJavaPath,
    aidlGenerate <<= aidlGenerateTask,

    resourceDirectories <+= (mainAssetsPath),

    cachePasswords := false,

    // Auto-manifest settings
    manifestRewriteRules := Seq.empty,

    // Migrate settings from the defaults in Compile
    sourceDirectory <<= sourceDirectory in Compile,
    sourceDirectories <<= sourceDirectories in Compile,
    resourceDirectory <<= resourceDirectory in Compile,
    resourceDirectories <<= resourceDirectories in Compile,
    javaSource <<= javaSource in Compile,
    scalaSource <<= scalaSource in Compile,
    dependencyClasspath <<= dependencyClasspath in Compile,

    // Set compile options
    unmanagedJars <++= (libraryJarPath) map (_.map(Attributed.blank(_))),
    classpathTypes := Set("jar", "bundle", "so"),
    sourceGenerators <+= (apklibSources, aaptGenerate, aidlGenerate) map (_ ++ _ ++ _)
  ))
}
