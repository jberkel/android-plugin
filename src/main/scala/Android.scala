import sbt._
import Keys._

import AndroidKeys._
import AndroidHelpers._
import DefaultAValues._

object Android extends Plugin {

  /** Base Task definitions */
  private def aptGenerateTask: Project.Initialize[Task[Unit]] = 
    (manifestPackage, aaptPath, manifestPath, mainResPath, jarPath, managedJavaPath) map {
    (mPackage, aPath, mPath, resPath, jPath, javaPath) => 
    Process (<x>
      {aPath.absolutePath} package --auto-add-overlay -m
        --custom-package {manifestPackage}
        -M {mPath.absolutePath}
        -S {resPath.absolutePath}
        -I {jPath.absolutePath}
        -J {javaPath.absolutePath}
    </x>) !
  }

  private def aidlGenerateTask: Project.Initialize[Task[Unit]] = 
    (sourceDirectories, idlPath, managedJavaPath, javaSource) map {
    (sDirs, idPath, javaPath, jSource) =>
    val aidlPaths = sDirs.map(_ * "*.aidl").reduceLeft(_ +++ _).get
    val processor = if (aidlPaths.isEmpty)
      Process(true)
    else
      aidlPaths.map { ap =>
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
    processor !
  }

  import Installable.installableSettings

  override lazy val settings = inConfig(AndroidConfig)(installableSettings ++ Seq (
    // Handle the delegates for android settings
    classDirectory <<= (classDirectory in Compile).identity,
    sourceDirectory <<= (sourceDirectory in Compile).identity,
    sourceDirectories <<= (sourceDirectories in Compile).identity,
    resourceDirectory <<= (resourceDirectory in Compile).identity,
    resourceDirectories <<= (resourceDirectories in Compile).identity,
    javaSource <<= (javaSource in Compile).identity,
    managedClasspath <<= (managedClasspath in Runtime).identity,
    fullClasspath <<= (fullClasspath in Runtime).identity,

    aaptName := DefaultAaaptName,
    adbName := DefaultAadbName,
    aidlName := DefaultAaidlName,
    dxName := DefaultDxName,
    manifestName := DefaultAndroidManifestName, 
    jarName := DefaultAndroidJarName, 
    mapsJarName := DefaultMapsJarName,
    assetsDirectoryName := DefaultAssetsDirectoryName,
    resDirectoryName := DefaultResDirectoryName,
    classesMinJarName := DefaultClassesMinJarName,
    classesDexName := DefaultClassesDexName,
    resourcesApkName := DefaultResourcesApkName,
    dxJavaOpts := DefaultDxJavaOpts,
    manifestSchema := DefaultManifestSchema, 
    envs := DefaultEnvs, 

    packageApkName <<= (artifact) (_.name + ".apk"),
    osDxName <<= (dxName) (_ + osBatchSuffix),

    apiLevel <<= (minSdkVersion, platformName) { (min, pName) =>
      min.getOrElse(platformName2ApiLevel(pName))
    },
    manifestPackage <<= (manifestPath) {
      manifest(_).attribute("package").getOrElse(error("package not defined")).text
    },
    minSdkVersion <<= (manifestPath, manifestSchema)(usesSdk(_, _, "minSdkVersion")),
    maxSdkVersion <<= (manifestPath, manifestSchema)(usesSdk(_, _, "maxSdkVersion")),

    toolsPath <<= (sdkPath) (_ / "tools"),
    dbPath <<= (platformToolsPath, adbName) (_ / _),
    platformPath <<= (sdkPath, platformName) (_ / "platforms" / _),
    platformToolsPath <<= (sdkPath) (_ / "platform-tools"),
    aaptPath <<= (platformToolsPath, aaptName) (_ / _),
    idlPath <<= (platformToolsPath, aidlName) (_ / _),
    dxPath <<= (platformToolsPath, osDxName) (_ / _),
    manifestPath <<= (sourceDirectory, manifestName) (_ / _),
    jarPath <<= (platformPath, jarName) (_ / _),
    nativeLibrariesPath <<= (sourceDirectory) (_ / "libs"),
    addonsPath <<= (sdkPath, apiLevel) { (sPath, api) =>
      sPath / "add-ons" / ("addon_google_apis_google_inc_" + api) / "libs"
    },
    mapsJarPath <<= (addonsPath) (_ / DefaultMapsJarName),
    mainAssetsPath <<= (sourceDirectory, assetsDirectoryName) (_ / _),
    mainResPath <<= (sourceDirectory, resDirectoryName) (_ / _),
    managedJavaPath := file("src_managed") / "main" / "java",
    classesMinJarPath <<= (target, classesMinJarName) (_ / _),
    classesDexPath <<= (target, classesDexName) (_ / _),
    resourcesApkPath <<= (target, resourcesApkName) (_ / _),
    packageApkPath <<= (target, packageApkName) (_ / _),
    skipProguard := false,

    addonsJarPath <<= (manifestPath, manifestSchema, mapsJarPath) { 
      (mPath, man, mapsPath) =>
      for {
        lib <- manifest(mPath) \ "application" \ "uses-library"
        p = lib.attribute(man, "name").flatMap {
          _.text match {
            case "com.google.android.maps" => Some(mapsPath)
            case _ => None
          }
        }
        if p.isDefined
      } yield p.get 
    },

    proguardOption := "",
    libraryJarPath <<= (jarPath, addonsJarPath) (_ +++ _ get),
    proguardExclude <<= 
      (libraryJarPath, classDirectory, resourceDirectory, managedClasspath) map {
        (libPath, classDirectory, resourceDirectory, managedClasspath) =>
          val temp = libPath +++ classDirectory +++ resourceDirectory 
          managedClasspath.foldLeft(temp)(_ +++ _.data) get
      },
    proguardInJars <<= (fullClasspath, proguardExclude) map {
      (runClasspath, proguardExclude) =>
      runClasspath.map(_.data) --- proguardExclude get
    },

    aptGenerate <<= aptGenerateTask,
    aidlGenerate <<= aidlGenerateTask,

    unmanagedJars in Compile <++= (libraryJarPath) map (_.map(Attributed.blank(_))), 

    sdkPath <<= (envs) { es => 
      determineAndroidSdkPath(es).getOrElse(error(
        "Android SDK not found. You might need to set %s".format(es.mkString(" or "))
      ))
    },

    sourceDirectories <+= (managedJavaPath).identity,
    cleanFiles <+= (managedJavaPath).identity,
    resourceDirectories <+= (mainAssetsPath).identity, 

    compile in Compile  <<= compile in Compile dependsOn (aptGenerate, aidlGenerate),

    screenshotDevice <<= (dbPath) map { p => 
      screenshot(false, false, p.absolutePath).getOrElse(error("could not get screenshot")).toFile("png", "device.png")
      file("device.png")
    },
    screenshotEmulator <<= (dbPath) map { p => 
      screenshot(true, false, p.absolutePath).getOrElse(error("could not get screenshot")).toFile("png", "emulator.png")
      file("emulator.png")
    }
  ))
}
