package sbtandroid

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
            s.log.info("Copied native library: " + target.toString)
        }

      // Clean up stale native libraries
      for (path <- IO.listFiles(natives / "armeabi") ++ IO.listFiles(natives / "armeabi-v7a")) {
        s.log.debug("Checking native library: " + path.toString)
        if (path.name.endsWith(".so") && !copied.contains(path)) {
          IO.delete(path)
          s.log.debug("Deleted native library: " + path.toString)
        }
      }
    }
  }

  private def apklibSourcesTask =
    (apklibDependencies, streams) map {
    (projectLibs, s) => {
      if (!projectLibs.isEmpty) {
        s.log.debug("Generating source files from ApkLibs")
        val xs = for (
          l <- projectLibs;
          f <- l.sources
        ) yield f

        s.log.info("Generated " + xs.size + " source files from " + projectLibs.size + " ApkLibs")
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
    (update, apklibBaseDirectory, apklibSourceManaged, apklibResourceManaged, resourceManaged, streams) map {
    (updateReport, apklibBaseDirectory, apklibSourceManaged, apklibResourceManaged, resManaged, s) => {

      // We want to extract every apklib in the classpath that is not already
      // set to provided (which should mean that another project already
      // provides the ApkLib).
      val allApklibs = updateReport.matching(artifactFilter(`type` = "apklib"))
      val providedApklibs = updateReport.matching(configurationFilter(name = "provided"))
      val apklibs = allApklibs --- providedApklibs get

      // Make the destination directories
      apklibBaseDirectory.mkdirs
      apklibSourceManaged.mkdirs
      apklibResourceManaged.mkdirs

      // Extract the ApkLibs
      apklibs map { apklib =>

        // Unzip the apklib to a temporary directory
        s.log.info("Extracting apklib " + apklib.name)
        val dest = apklibResourceManaged / apklib.base
        val unzipped = IO.unzip(apklib, dest)

        // Move the sources to ApkLib source directory
        def moveContents(fromDir: File, toDir: File) = {
          toDir.mkdirs()
          val pairs = for (
            file <- unzipped;
            rel <- IO.relativize(fromDir, file)
          ) yield (file, toDir / rel)
          IO.move(pairs)
          pairs map { case (_,t) => t }
        }
        val sources = moveContents(dest / "src", apklibSourceManaged)

        // Read the package name from the manifest
        val manifest = dest / "AndroidManifest.xml"
        val pkgName = XML.loadFile(manifest).attribute("package").get.head.text

        // Return a LibraryProject instance with some info about this ApkLib
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
    (manifestPackage, aaptPath, manifestPath, resPath, jarPath,
    managedJavaPath, generatedProguardConfigPath, apklibDependencies,
    apklibSourceManaged, streams, useDebug) map {

    (mPackage, aPath, mPath, rPath, jarPath, javaPath, proGen, apklibs, apklibJavaPath, s, useDebug) =>

    val libraryResPathArgs = rPath.flatMap(p => Seq("-S", p.absolutePath))

    val libraryAssetPathArgs = for (
      lib <- apklibs;
      d <- lib.assetsDir.toSeq;
      arg <- Seq("-A", d.absolutePath)
    ) yield arg

    def runAapt(`package`: String, outJavaPath: File, args: String*) {
      s.log.info("Running AAPT for package " + `package`)

      val aapt = Seq(aPath.absolutePath, "package", "--auto-add-overlay", "-m",
        "--custom-package", `package`,
        "-M", mPath.head.absolutePath,
        "-I", jarPath.absolutePath,
        "-J", outJavaPath.absolutePath,
        "-G", proGen.absolutePath) ++
        args ++
        libraryResPathArgs ++
        libraryAssetPathArgs

      if (aapt.run(false).exitValue != 0) sys.error("error generating resources")
    }

    // Run aapt to generate resources for the main package
    runAapt(mPackage, javaPath)

    // Run aapt to generate resources for each apklib dependency
    apklibs.foreach(lib => runAapt(lib.pkgName, apklibJavaPath, "--non-constant-id"))

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
    (apklibJavaPath ** "R.java" get) ++
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
      case Some(m) => preinstalled exists (pm =>
        pm.organization == m.organization &&
        pm.name == m.name)
      case None => false
    }
  }

  /**
   * Returns the internal dependencies for the "provided" scope only
   */
  def providedInternalDependenciesTask(proj: ProjectRef, struct: Load.BuildStructure) = {
    // "Provided" dependencies of a ResolvedProject
    def providedDeps(op: ResolvedProject): Seq[ProjectRef] = {
      op.dependencies
        .filter(p => (p.configuration getOrElse "") == "provided")
        .map(_.project)
    }

    // Collect every "provided" dependency in the dependency graph
    def collectDeps(projRef: ProjectRef): Seq[ProjectRef] = {
      val deps = Project.getProject(projRef, struct).toSeq.flatMap(providedDeps)
      deps.flatMap(ref => ref +: collectDeps(ref)).distinct
    }

    // Return the list of "provided" internal dependencies for the ProjectRef
    // in argument.
    collectDeps(proj)
      .flatMap(exportedProducts in (_, Compile) get struct.data)
      .join.map(_.flatten.files)
  }

  val providedInternalDependencies = TaskKey[Seq[File]]("provided-internal-dependencies")

  lazy val globalSettings: Seq[Setting[_]] = Seq(
    // By default, use the first device we find as the ADB target
    adbTarget in Global := AndroidDefaultTargets.Auto,

    // By default, don't cache passwords
    cachePasswords in Global := false,

    // By default, no additional Proguard options and optimizations
    proguardOptions := Seq.empty,
    proguardOptimizations := Seq.empty,

    // Platform path for the current project
    platformPath <<= (sdkPath, platformName) (_ / "platforms" / _),

    // Path to the platform android.jar for the current project
    jarPath <<= (platformPath, jarName) (_ / _),
    libraryJarPath <<= (jarPath (_ get)),

    // By default, if preloading is enabled, preload the Scala library
    preloadFilters := Seq(filterName("scala-library")),

    // Default IntelliJ configuration (for sbtidea integration)
    ideaConfiguration := Compile,

    // Default key alias
    keyalias := "alias_name",

    // Release defaults to the Release scope
    release <<= release in Release,

    // Apk defaults to the Compile scope
    apk <<= apk in Compile
  )

  lazy val settings: Seq[Setting[_]] = (Seq (

    // Path to the Proguard-ed class JAR
    classesMinJarName <<= (artifact, configuration, version) (
      (a, c, v) => "classes-%s-%s-%s.min.jar".format(a.name, c.name, v) ),

    // Path to the dexed class file
    classesDexName <<= (artifact, configuration, version) (
      (a, c, v) => "classes-%s-%s-%s.dex".format(a.name, c.name, v) ),

    // Name and path to the resource APK
    resourcesApkName <<= (artifact, configuration, version) (
      (a, c, v) => "resources-%s-%s-%s.apk".format(a.name, c.name, v) ),
    resourcesApkPath <<= (target, resourcesApkName) (_ / _),

    // Name and path to the final APK
    packageApkName <<= (artifact, configuration, versionName) map (
      (a, c, v) => "%s-%s-%s.apk".format(a.name, c.name, v) ),
    packageApkPath <<= (target, packageApkName) map (_ / _),

    // Name and path to the final ApkLib
    packageApkLibName <<= (artifact, configuration, versionName) map (
      (a, c, v) => "%s-%s-%s.apklib".format(a.name, c.name, v) ),
    packageApkLibPath <<= (target, packageApkLibName) map (_ / _),

    // Path to the manifest file
    manifestPath <<= (sourceDirectory, manifestName) map((s,m) => Seq(s / m)),

    // Package information, extracted from the manifest
    manifestPackage <<= findPath,
    manifestPackageName <<= findPath storeAs manifestPackageName triggeredBy manifestPath,
    minSdkVersion <<= (manifestPath, manifestSchema) map ( (p,s) => usesSdk(p.head, s, "minSdkVersion")),
    maxSdkVersion <<= (manifestPath, manifestSchema) map ( (p,s) => usesSdk(p.head, s, "maxSdkVersion")),
    versionName <<= (manifestPath, manifestSchema, version) map ((p, schema, version) =>
        manifest(p.head).attribute(schema, "versionName").map(_.text).getOrElse(version)
    ),

    // Path to the native libraries (.so) base directory 
    nativeLibrariesPath <<= (sourceDirectory) (_ / "libs"),

    // Main asset and resource paths
    mainAssetsPath <<= (sourceDirectory, assetsDirectoryName) (_ / _),
    mainResPath <<= (sourceDirectory, resDirectoryName) (_ / _) map (x=> x),

    // Managed sources and resources
    managedSourceDirectories <+= apklibSourceManaged,
    managedJavaPath <<= (sourceManaged) (_ / "java"),
    managedScalaPath <<= (sourceManaged) ( _ / "scala"),
    managedNativePath <<= (sourceManaged) (_ / "native_libs"),

    // Resource paths
    //
    // By default, include the main resource path, as well as the resources
    // from additional ApkLib dependencies.
    resPath := Seq(),
    resPath <+= mainResPath,
    resPath <++= apklibDependencies map (apklibs => apklibs.flatMap(_.resDir)),

    // Path to the resources APK file
    resourcesApkPath <<= (target, resourcesApkName) (_ / _),

    // Assets go into the resource directories
    resourceDirectories <<= resourceDirectories in Compile,
    resourceDirectories <+= (mainAssetsPath),

    // ApkLib paths
    apklibBaseDirectory <<= crossTarget (_ / "apklib_managed"),
    apklibSourceManaged <<= apklibBaseDirectory (_ / "src"),
    apklibResourceManaged <<= apklibBaseDirectory (_ / "res"),
    apklibDependencies <<= apklibDependenciesTask,
    apklibPackage <<= apklibPackageTask,
    apklibSources <<= apklibSourcesTask,

    // Output path of the DX command
    dxOutputPath <<= (target, classesDexName) (_ / _),

    // Inputs for the DX command
    dxInputs <<=
      (proguard, includedClasspath, classDirectory) map (
      (proguard, includedClasspath, classDirectory) => proguard match {
        case Some(f) => Seq(f)
        case None => includedClasspath :+ classDirectory
      }
    ),

    // Paths to be predexed by DX to improve build times.
    //
    // Usually, libraries that won't change much over time, and, by default,
    // the inputs that are part of the managed classpath.
    dxPredex <<= (managedClasspath, dxInputs) map {
      (cp, inputs) => { cp filter (inputs contains _.data) files }
    },

    // Provided internal dependencies (usually, class directories from a
    // dependency project set as "provided")
    providedInternalDependencies <<= (thisProjectRef, buildStructure) flatMap providedInternalDependenciesTask,

    // The full input classpath
    inputClasspath <<= (dependencyClasspath) map { dcp =>
      dcp filterNot (cpe => cpe.get(artifact.key) match {
        case Some(k) => k.`type` == "so"
        case None => false
      }) map (_.data)
    },

    // The included classpath entries
    includedClasspath <<=
      (update, libraryJarPath, usePreloaded, dependencyClasspath, preinstalledModules, preloadFilters, providedInternalDependencies) map {
      (update, libraryJarPath, usePreloaded, dependencyClasspath, preinstalledModules, preloadFilters, providedInternalDependencies) =>

      // Filters out the entries that are _not_ to be included in the final APK
      val notIncludedFilters = (
        (if (usePreloaded) preloadFilters else Seq.empty) ++
        (preinstalledModules map (filterModule _))
      )

      // Provided dependencies that are not to be included in the APK
      val provided = (
        providedInternalDependencies ++
        update.select(Set("provided")) ++
        libraryJarPath
      )

      // Filter the full classpath
      dependencyClasspath.filterNot { cpe =>
        (notIncludedFilters exists (f => f(cpe))) ||
        (provided contains cpe.data)
      }.files
    },

    // The provided classpath entries are those that are in `fullClasspath` but
    // not in `includedClasspath`.
    providedClasspath <<= (inputClasspath, includedClasspath) map ((in, incl) =>
      in filterNot (incl contains _)),

    // Path to Proguard's output JAR
    proguardOutputPath <<= (target, classesMinJarName) (_ / _),

    // Path to the generated (with aapt -G) Proguard configuration
    generatedProguardConfigPath <<= (target, generatedProguardConfigName) (_ / _),

    // Create the managed Java path
    makeManagedJavaPath <<= directory(managedJavaPath),

    copyNativeLibraries <<= copyNativeLibrariesTask,

    // AAPT and AIDL source generation
    aaptGenerate <<= aaptGenerateTask,
    aaptGenerate <<= aaptGenerate dependsOn makeManagedJavaPath,
    aidlGenerate <<= aidlGenerateTask,

    // Manifest generator rules
    manifestRewriteRules := Seq.empty,

    // Migrate settings from the defaults in Compile
    sourceDirectory <<= sourceDirectory in Compile,
    sourceDirectories <<= sourceDirectories in Compile,
    resourceDirectory <<= resourceDirectory in Compile,
    javaSource <<= javaSource in Compile,
    scalaSource <<= scalaSource in Compile,
    dependencyClasspath <<= dependencyClasspath in Compile,
    managedClasspath <<= managedClasspath in Compile,

    // Add the Android library to the classpath
    unmanagedJars <++= (libraryJarPath) map (_.map(Attributed.blank(_))),

    // Set the default classpath types
    classpathTypes := Set("jar", "bundle", "so"),

    // Configure the source generators
    sourceGenerators <+= (apklibSources, aaptGenerate, aidlGenerate) map (_ ++ _ ++ _)
  ))
}
