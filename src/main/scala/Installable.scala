import proguard.{Configuration=>ProGuardConfiguration, ProGuard, ConfigurationParser}

import sbt._
import Keys._
import AndroidKeys._
import AndroidHelpers._

object Installable {

  private def installTask(emulator: Boolean) = (dbPath, packageApkPath) map { (dp, p) =>
    adbTask(dp.absolutePath, emulator, "install "+p.absolutePath) 
  }

  private def reinstallTask(emulator: Boolean) = (dbPath, packageApkPath) map { (dp, p) =>
    adbTask(dp.absolutePath, emulator, "install -r"+p.absolutePath)
  }

  private def uninstallTask(emulator: Boolean) = (dbPath, manifestPackage) map { (dp, m) =>
    adbTask(dp.absolutePath, emulator, "uninstall "+m)
  }

  private def startTask(emulator: Boolean) = 
    (dbPath, manifestSchema, manifestPackage, manifestPath) map { 
      (dp, schema, mPackage, amPath) =>
      adbTask(dp.absolutePath, 
              emulator, 
              "shell am start -a android.intent.action.MAIN -n "+mPackage+"/"+
              launcherActivity(schema, amPath, mPackage))
  }

  private def launcherActivity(schema: String, amPath: File, mPackage: String) = {
    val launcher = for (
         activity <- (manifest(amPath) \\ "activity");
         action <- (activity \\ "action");
         val name = action.attribute(schema, "name").getOrElse(error{ 
            "action name not defined"
          }).text;
         if name == "android.intent.action.MAIN"
    ) yield {
      val act = activity.attribute(schema, "name").getOrElse(error("activity name not defined")).text
      if (act.contains(".")) act else mPackage+"."+act
    }
    launcher.headOption.getOrElse("")
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

  lazy val installableSettings = Seq (
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

    packageDebug := (),
    packageRelease := (),
    packageDebug <<= packageDebug dependsOn aaptPackage,
    packageRelease <<= packageRelease dependsOn aaptPackage
  )
}
