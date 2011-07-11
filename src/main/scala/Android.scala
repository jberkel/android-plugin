import proguard.{Configuration=>ProGuardConfiguration, ProGuard, ConfigurationParser}

import sbt._
import Keys._

import AndroidKeys._
import AndroidHelpers._
import DefaultAValues._

object Android extends Plugin {

  /** Base Task definitions */
  private def aptGenerateTask: Project.Initialize[Task[Unit]] = 
    (manifestPackage, aaptPath, manifestPath, mainResPath, jarPath, managedJavaPath) map {
    (mPackage, aPath, mPath, resPath, jPath, javaPath) => Process (<x>
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
    if (aidlPaths.isEmpty)
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
  }

  private def aaptPackageTask: Project.Initialize[Task[Unit]] = 
  (aaptPath, manifestPath, mainResPath, mainAssetsPath, jarPath, resourcesApkPath) map {
    (apPath, manPath, rPath, assetPath, jPath, resApkPath) => Process(<x>
      {apPath} package --auto-add-overlay -f
        -M {manPath}
        -S {rPath}
        -A {assetPath}
        -I {jPath}
        -F {resApkPath}
    </x>) !
  }
 
  private def dxTask: Project.Initialize[Task[Unit]] = 
    (skipProguard, dxJavaOpts, dxPath, classDirectory, 
     proguardInJars, classesDexPath, classesMinJarPath) map { 
      (skipProguard, javaOpts, dxPath, classDirectory, 
     proguardInJars, classesDexPath, classesMinJarPath) =>
      val outputs = if (!skipProguard) {
        classesMinJarPath get
      } else {
        proguardInJars +++ classDirectory get
      }
      Process(
      <x>
        {dxPath} {dxMemoryParameter(javaOpts)}
        --dex --output={classesDexPath}
        {outputs.mkString(" ")}
      </x>
      ) !
    }

  private def proguardTask: Project.Initialize[Task[Unit]] =
    (scalaInstance, classDirectory, proguardInJars, 
     classesMinJarPath, libraryJarPath, manifestPackage, proguardOption) map {
      (scalaInstance, classDirectory, proguardInJars, 
       classesMinJarPath, libraryJarPath, manifestPackage, proguardOption) =>
      val args = 
            "-injars" :: classDirectory.absolutePath + Path.sep +
             scalaInstance.libraryJar.absolutePath + 
             "(!META-INF/MANIFEST.MF,!library.properties)" +
             (if (!proguardInJars.isEmpty)
             Path.sep +
             proguardInJars.map(_+"(!META-INF/MANIFEST.MF,!**/R.class,!**/R$*.class,!**/TR.class,!**/TR$*.class)").mkString(Path.sep.toString) else "") ::
             "-outjars" :: classesMinJarPath.absolutePath ::
             "-libraryjars" :: libraryJarPath.mkString(Path.sep.toString) ::
             "-dontwarn" :: "-dontoptimize" :: "-dontobfuscate" ::
             "-dontnote scala.Enumeration" ::
             "-dontnote org.xml.sax.EntityResolver" ::
             "-keep public class * extends android.app.Activity" ::
             "-keep public class * extends android.app.Service" ::
             "-keep public class * extends android.appwidget.AppWidgetProvider" ::
             "-keep public class * extends android.content.BroadcastReceiver" ::
             "-keep public class * extends android.content.ContentProvider" ::
             "-keep public class * extends android.view.View" ::
             "-keep public class * extends android.app.Application" ::
             "-keep public class "+manifestPackage+".** { public protected *; }" ::
             "-keep public class * implements junit.framework.Test { public void test*(); }" :: proguardOption :: Nil
      val config = new ProGuardConfiguration
      new ConfigurationParser(args.toArray[String]).parse(config)
      new ProGuard(config).execute
    }

  // TODO: Move the managed sources and resources outside of AndroidConfig
  override val settings = inConfig(AndroidConfig)(Seq (
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

    sdkPath <<= (envs) { es => 
      determineAndroidSdkPath(es).getOrElse(error(
        "Android SDK not found. You might need to set %s".format(es.mkString(" or "))
      ))
    },

    sourceDirectories <+= (managedJavaPath).identity,
    cleanFiles <+= (managedJavaPath).identity,
    resourceDirectories <+= (mainAssetsPath).identity, 

    compile in Compile  <<= compile in Compile dependsOn (aptGenerate, aidlGenerate),

    // Installable Tasks
    installEmulator <<= installTask(emulator = true),
    uninstallEmulator <<= uninstallTask(emulator = true),
    reinstallEmulator <<= reinstallTask(emulator = true),
    
    installDevice <<= installTask(emulator = false),
    uninstallDevice <<= uninstallTask(emulator = false),
    reinstallDevice <<= reinstallTask(emulator = false),

    aaptPackage <<= aaptPackageTask,
    aaptPackage <<= aaptPackage dependsOn dx,
    dx <<= dxTask,
    dx <<= dx dependsOn (compile in Compile),

    proguard <<= proguardTask,
    proguard <<= proguard dependsOn (compile in Compile),

    startDevice <<= startTask(false),
    startEmulator <<= startTask(true),
    startDevice <<= startDevice dependsOn reinstallDevice,
    startEmulator <<= startEmulator dependsOn reinstallEmulator,

    // Fill in implemenation later
    packageDebug := (),
    packageRelease := (),
    packageDebug <<= packageDebug dependsOn aaptPackage,
    packageRelease <<= packageRelease dependsOn aaptPackage,
    
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
