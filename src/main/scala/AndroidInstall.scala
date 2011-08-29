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

  private def aaptPackageTask: Project.Initialize[Task[File]] =
  (aaptPath, manifestPath, mainResPath, mainAssetsPath, jarPath, resourcesApkPath) map {
    (apPath, manPath, rPath, assetPath, jPath, resApkPath) => Process(<x>
      {apPath} package --auto-add-overlay -f
        -M {manPath}
        -S {rPath}
        -A {assetPath}
        -I {jPath}
        -F {resApkPath}
    </x>).!
    resApkPath
  }

  private def dxTask: Project.Initialize[Task[File]] =
    (scalaInstance, dxJavaOpts, dxPath, classDirectory,
     proguardInJars, proguard, classesDexPath, streams) map {
    (scalaInstance, dxJavaOpts, dxPath, classDirectory,
     proguardInJars, proguard, classesDexPath, streams) =>

      val inputs = proguard match {
        case Some(file) => file get
        case None       => classDirectory +++ proguardInJars --- scalaInstance.libraryJar get
      }
      val uptodate = classesDexPath.exists &&
        !inputs.exists (_.lastModified > classesDexPath.lastModified)

      if (!uptodate) {
        val dxCmd = String.format("%s %s --dex --output=%s %s",
          dxPath, dxMemoryParameter(dxJavaOpts), classesDexPath, inputs.mkString(" "))
        streams.log.debug(dxCmd)
        streams.log.info("Dexing "+classesDexPath)
        streams.log.debug(dxCmd !!)
      } else streams.log.debug("dex file uptodate, skipping")

      classesDexPath
    }

  private def proguardTask: Project.Initialize[Task[Option[File]]] =
    (skipProguard, classDirectory, proguardInJars, streams,
     classesMinJarPath, libraryJarPath, manifestPackage, proguardOption) map {
      (skipProguard, classDirectory, proguardInJars, streams,
       classesMinJarPath, libraryJarPath, manifestPackage, proguardOption) =>
      skipProguard match {
        case false =>
          val manifestr = List("!META-INF/MANIFEST.MF", "R.class", "R$*.class",
                               "TR.class", "TR$.class", "library.properties")
          val sep = JFile.pathSeparator
          val args =
                "-injars" :: classDirectory.absolutePath + sep +
                 (if (!proguardInJars.isEmpty)
                 proguardInJars.map(_+manifestr.mkString("(", ",!**/", ")")).mkString(sep) else "") ::
                 "-outjars" :: classesMinJarPath.absolutePath ::
                 "-libraryjars" :: libraryJarPath.mkString(sep) ::
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
                 "-keep public class * implements junit.framework.Test { public void test*(); }" ::
                 proguardOption :: Nil
          val config = new ProGuardConfiguration
          new ConfigurationParser(args.toArray[String]).parse(config)
          streams.log.debug("executing proguard: "+args.mkString("\n"))
          new ProGuard(config).execute
          Some(classesMinJarPath)
        case true =>
          streams.log.info("Skipping Proguard")
          None
      }
    }

  private def packageTask(debug: Boolean):Project.Initialize[Task[File]] = (packageConfig, streams) map { (c, s) =>
    val builder = new ApkBuilder(c, debug)
    builder.build.fold(s.log.error(_), s.log.info(_))
    s.log.debug(builder.outputStream.toString)
    c.packageApkPath
  }

  lazy val installerTasks = Seq (
    installEmulator <<= installTask(emulator = true),
    reinstallEmulator <<= reinstallTask(emulator = true),

    installDevice <<= installTask(emulator = false),
    reinstallDevice <<= reinstallTask(emulator = false)
  )

  lazy val aliasKeys = Seq(installEmulator,reinstallEmulator,
                               installDevice,reinstallDevice)

  lazy val defaultAliases = aliasKeys map (k => k <<= (k in Android).identity)

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (installerTasks ++
    aliasKeys.map(t => t <<= t dependsOn packageDebug) ++ Seq (
    uninstallEmulator <<= uninstallTask(emulator = true),
    uninstallDevice <<= uninstallTask(emulator = false),

    makeAssetPath <<= directory(mainAssetsPath),

    aaptPackage <<= aaptPackageTask,
    aaptPackage <<= aaptPackage dependsOn (makeAssetPath, dx),
    dx <<= dxTask,
    dx <<= dx dependsOn proguard,

    cleanApk <<= (packageApkPath) map (IO.delete(_)),

    proguard <<= proguardTask,
    proguard <<= proguard dependsOn (compile in Compile),

    packageConfig <<=
      (toolsPath, packageApkPath, resourcesApkPath, classesDexPath,
       nativeLibrariesPath, classesMinJarPath, resourceDirectory)
      (ApkConfig(_, _, _, _, _, _, _)),

    packageDebug <<= packageTask(true),
    packageRelease <<= packageTask(false)
  ) ++ Seq(packageDebug, packageRelease).map {
    t => t <<= t dependsOn (cleanApk, aaptPackage)
  }) ++ defaultAliases
}
