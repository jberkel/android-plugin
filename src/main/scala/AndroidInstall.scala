package org.scalasbt.androidplugin

import java.util.Properties
import proguard.{Configuration=>ProGuardConfiguration, ProGuard, ConfigurationParser}

import sbt._
import Keys._
import AndroidPlugin._
import AndroidHelpers._

import java.io.{File => JFile}

object AndroidInstall {

  private def installTask(emulator: Boolean) = (dbPath, packageApkPath, streams) map { (dp, p, s) =>
    adbTask(dp.absolutePath, emulator, s, "install", "-r ", p.absolutePath)
    ()
  }

  private def uninstallTask(emulator: Boolean) = (dbPath, manifestPackage, streams) map { (dp, m, s) =>
    adbTask(dp.absolutePath, emulator, s, "uninstall", m)
    ()
  }

  private def aaptPackageTask: Project.Initialize[Task[File]] =
  (aaptPath, manifestPath, mainResPath, mainAssetsPath, jarPath, resourcesApkPath, extractApkLibDependencies, streams) map {
    (apPath, manPath, rPath, assetPath, jPath, resApkPath, apklibs, s) =>

    val libraryResPathArgs = for (
      lib <- apklibs;
      d <- lib.resDir.toSeq;
      arg <- Seq("-S", d.absolutePath)
    ) yield arg

    val aapt = Seq(apPath.absolutePath, "package", "--auto-add-overlay", "-f",
        "-M", manPath.head.absolutePath,
        "-S", rPath.absolutePath,
        "-A", assetPath.absolutePath,
        "-I", jPath.absolutePath,
        "-F", resApkPath.absolutePath) ++
        libraryResPathArgs
    s.log.debug("packaging: "+aapt.mkString(" "))
    if (aapt.run(false).exitValue != 0) sys.error("error packaging resources")
    resApkPath
  }

  private def dxTask: Project.Initialize[Task[File]] =
    (dxPath, dxMemory, target, proguard, dxInputs, dxPredex,
      proguardOptimizations, classDirectory, dxOutputPath, scalaInstance, streams) map {
    (dxPath, dxMemory, target, proguard, dxInputs, dxPredex,
      proguardOptimizations, classDirectory, dxOutputPath, scalaInstance, streams) =>

      // Main dex command
      def dexing(inputs: Seq[JFile], output: JFile) {
        val uptodate = output.exists && inputs.forall(input =>
          input.isDirectory match {
            case true =>
              (input ** "*").get.forall(_.lastModified <= output.lastModified)
            case false =>
              input.lastModified <= output.lastModified
          }
        )

        if (!uptodate) {
          val noLocals = if (proguardOptimizations.isEmpty) "" else "--no-locals"
          val dxCmd = (Seq(dxPath.absolutePath,
                          dxMemoryParameter(dxMemory),
                          "--dex", noLocals,
                          "--num-threads="+java.lang.Runtime.getRuntime.availableProcessors,
                          "--output="+output.getAbsolutePath) ++
                          inputs.map(_.absolutePath)).filter(_.length > 0)
          streams.log.debug(dxCmd.mkString(" "))
          streams.log.info("Dexing "+output.getAbsolutePath)
          streams.log.debug(dxCmd !!)
        } else streams.log.debug("dex file " + output.getAbsolutePath + " uptodate, skipping")
      }

      // First, predex the inputs in dxPredex
      val dxPredexInputs = dxInputs filter (dxPredex contains _) map { jarPath =>

        // Generate the output path
        val outputPath = target / (jarPath.getName + ".apk")

        // Predex the library
        dexing(Seq(jarPath), outputPath)

        // Return the output path
        outputPath
      }

      // Non-predexed inputs
      val dxClassInputs = dxInputs filterNot (dxPredex contains _)

      // Generate the final DEX
      dexing(dxClassInputs +++ dxPredexInputs get, dxOutputPath)

      // Return the path to the generated final DEX file
      dxOutputPath
    }

  private def proguardTask: Project.Initialize[Task[Option[File]]] =
    (useProguard, proguardOptimizations, scalaInstance, classDirectory, proguardInJars, proguardLibraryJars, streams,
     proguardOutputPath, manifestPackage, proguardOptions, sourceManaged) map {
    (useProguard, proguardOptimizations, scalaInstance, classDirectory, proguardInJars, proguardLibraryJars, streams,
     proguardOutputPath, manifestPackage, proguardOptions, sourceManaged) =>
      if (useProguard) {
          val optimizationOptions = if (proguardOptimizations.isEmpty) Seq("-dontoptimize") else proguardOptimizations
          val manifestr = List("!META-INF/MANIFEST.MF", "R.class", "R$*.class",
                               "TR.class", "TR$.class", "library.properties")
          val sep = JFile.pathSeparator

          // Input class files
          val inClass = "\"" + classDirectory.absolutePath + "\""

          // Input library JARs to be included in the APK
          val inJars = proguardInJars
                       .filterNot(s => (s == classDirectory) || (proguardLibraryJars contains s))
                       .map("\""+_+"\""+manifestr.mkString("(", ",!**/", ")"))

          // Input library JARs to be provided at runtime
          val inLibrary = proguardLibraryJars map ("\"" + _ + "\"")

          // Output JAR
          val outJar = "\""+proguardOutputPath.absolutePath+"\""

          // Configuration output file
          val targetConfiguration = (sourceManaged / "proguard.txt").toString

          // Proguard arguments
          val args = (
                 "-injars" :: inClass ::
                 "-injars" :: inJars.mkString(sep) ::
                 "-outjars" :: outJar ::
                 "-libraryjars" :: inLibrary.mkString(sep) ::
                 Nil) ++
                 optimizationOptions ++ (
                 "-printconfiguration " + targetConfiguration ::
                 "-dontwarn" :: "-dontobfuscate" ::
                 "-dontnote scala.Enumeration" ::
                 "-dontnote org.xml.sax.EntityResolver" ::
                 "-keep class scala.collection.SeqLike { public java.lang.String toString(); }" ::
                 "-keep class scala.reflect.ScalaSignature" ::
                 "-keep public class * extends android.app.Activity" ::
                 "-keep public class * extends android.app.Service" ::
                 "-keep public class * extends android.app.backup.BackupAgent" ::
                 "-keep public class * extends android.appwidget.AppWidgetProvider" ::
                 "-keep public class * extends android.content.BroadcastReceiver" ::
                 "-keep public class * extends android.content.ContentProvider" ::
                 "-keep public class * extends android.view.View" ::
                 "-keep public class * extends android.app.Application" ::
                 "-keep public class " + manifestPackage + ".** { *; }" ::
                 "-keep public class * implements junit.framework.Test { public void test*(); }" ::
                 """
                  -keepclassmembers class * implements java.io.Serializable {
                    private static final java.io.ObjectStreamField[] serialPersistentFields;
                    private void writeObject(java.io.ObjectOutputStream);
                    private void readObject(java.io.ObjectInputStream);
                    java.lang.Object writeReplace();
                    java.lang.Object readResolve();
                   }
                   """ :: Nil) ++ proguardOptions

          // Instantiate the Proguard configuration
          val config = new ProGuardConfiguration
          new ConfigurationParser(args.toArray[String], new Properties).parse(config)

          // Execute Proguard
          streams.log.info("Executing Proguard (configuration written to " + targetConfiguration + ")")
          streams.log.debug("Proguard configuration: "+args.mkString("\n"))
          new ProGuard(config).execute

          // Return the proguard-ed output JAR
          Some(proguardOutputPath)

      } else {
          streams.log.info("Skipping Proguard")
          None
      }
    }

  private val apkTask =
    (useDebug, packageConfig, streams) map { (debug, c, s) =>
      val builder = new ApkBuilder(c, debug)
      builder.build.fold(sys.error(_), s.log.info(_))
      s.log.debug(builder.outputStream.toString)
      c.packageApkPath
    }

  lazy val settings: Seq[Setting[_]] = Seq(

    // Resource generation (AAPT)
    makeAssetPath <<= directory(mainAssetsPath),
    aaptPackage <<= aaptPackageTask,
    aaptPackage <<= aaptPackage dependsOn (makeAssetPath, dx),

    // Dexing (DX)
    dx <<= dxTask,
    dxMemory := "-JXmx512m",

    // Clean generated APK
    cleanApk <<= (packageApkPath) map (IO.delete(_)),

    // Proguard
    proguard <<= proguardTask,
    proguard <<= proguard dependsOn (compile),

    // Final APK generation
    packageConfig <<=
      (toolsPath, packageApkPath, resourcesApkPath, dxOutputPath,
       nativeLibrariesPath, managedNativePath, dxInputs, resourceDirectory) map
      (ApkConfig(_, _, _, _, _, _, _, _)),

    apk <<= apkTask dependsOn (cleanApk, aaptPackage, copyNativeLibraries),

    // Package installation
    installEmulator <<= installTask(emulator = true) dependsOn apk,
    installDevice <<= installTask(emulator = false) dependsOn apk,

    // Package uninstallation
    uninstallEmulator <<= uninstallTask(emulator = true),
    uninstallDevice <<= uninstallTask(emulator = false)
  )
}
