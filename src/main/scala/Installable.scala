import proguard.{Configuration=>ProGuardConfiguration, ProGuard, ConfigurationParser}

import sbt._
import Keys._
import AndroidKeys._
import AndroidHelpers._

import java.io.{File => JFile}

object AndroidInstall {

  private def installTask(emulator: Boolean) = (dbPath, packageApkPath) map { (dp, p) =>
    adbTask(dp.absolutePath, emulator, "install "+p.absolutePath) 
  }

  private def reinstallTask(emulator: Boolean) = (dbPath, packageApkPath) map { (dp, p) =>
    adbTask(dp.absolutePath, emulator, "install -r "+p.absolutePath)
  }

  private def uninstallTask(emulator: Boolean) = (dbPath, manifestPackage) map { (dp, m) =>
    adbTask(dp.absolutePath, emulator, "uninstall "+m)
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
        classDirectory +++ proguardInJars get
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
            "-injars" :: classDirectory.absolutePath + JFile.pathSeparator +
             scalaInstance.libraryJar.absolutePath + 
             "(!META-INF/MANIFEST.MF,!library.properties)" +
             (if (!proguardInJars.isEmpty)
             JFile.pathSeparator +
             proguardInJars.map(_+"(!META-INF/MANIFEST.MF,!**/R.class,!**/R$*.class,!**/TR.class,!**/TR$*.class)").mkString(JFile.pathSeparator) else "") ::
             "-outjars" :: classesMinJarPath.absolutePath ::
             "-libraryjars" :: libraryJarPath.mkString(JFile.pathSeparator) ::
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

  private def packageTask(debug: Boolean) = (packageConfig, streams) map { (c, s) =>
    val builder = new ApkBuilder(c, debug)
    builder.build.fold(s.log.error(_), s.log.info(_))
    s.log.debug(builder.outputStream.toString)
  }

  lazy val installerKeys = Seq(installEmulator,reinstallEmulator,
                               installDevice,reinstallDevice)

  lazy val installerTasks = Seq (
    installEmulator <<= installTask(emulator = true),
    reinstallEmulator <<= reinstallTask(emulator = true),

    installDevice <<= installTask(emulator = false),
    reinstallDevice <<= reinstallTask(emulator = false)
  )

  lazy val settings: Seq[Setting[_]] = installerTasks ++ 
    installerKeys.map(t => t <<= t dependsOn packageDebug) ++ Seq (
    uninstallEmulator <<= uninstallTask(emulator = true),
    uninstallDevice <<= uninstallTask(emulator = false),

    makeAssetPath <<= directory(mainAssetsPath), 

    aaptPackage <<= aaptPackageTask,
    aaptPackage <<= aaptPackage dependsOn (makeAssetPath, dx),
    dx <<= dxTask,
    dx <<= dx dependsOn (compile in Compile),

    cleanApk <<= (packageApkPath) map (IO.delete(_)),

    proguard <<= proguardTask,
    proguard <<= proguard dependsOn (compile in Compile),

    packageConfig <<= 
      (toolsPath, packageApkPath, resourcesApkPath, 
       classesDexPath, nativeLibrariesPath, classesMinJarPath) (ApkConfig(_, _, _, _, _, _)),
    packageDebug <<= packageTask(true),
    packageRelease <<= packageTask(false),
    packageDebug <<= packageDebug dependsOn (cleanApk, aaptPackage),
    packageRelease <<= packageRelease dependsOn (cleanApk, aaptPackage)
  )
}
