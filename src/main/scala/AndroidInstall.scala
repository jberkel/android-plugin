package sbtandroid

import java.util.Properties
import proguard.{Configuration=>ProGuardConfiguration, ProGuard, ConfigurationParser, ConfigurationWriter}

import sbt._
import Keys._
import AndroidPlugin._
import AndroidHelpers._

import java.io.{File => JFile}

object AndroidInstall {

  /**
   * Task that installs a package on the target
   */
  private val installTask =
    (adbTarget, dbPath, packageApkPath, streams) map { (t, dp, p, s) =>
    s.log.info("Installing %s".format(p.name))
    t.installPackage(dp, s, p)
    ()
  }

  /**
   * Task that uninstalls a package from the target
   */
  private val uninstallTask =
    (adbTarget, dbPath, packageApkPath, streams) map { (t, dp, p, s) =>
    s.log.info("Uninstalling %s".format(p.name))
    t.uninstallPackage(dp, s, p)
    ()
  }

  private def aaptPackageTask: Project.Initialize[Task[File]] =
  (aaptPath, manifestPath, resPath, mainAssetsPath, jarPath, resourcesApkPath, apklibDependencies, streams) map {
    (apPath, manPath, rPath, assetPath, jPath, resApkPath, apklibs, s) =>

    val libraryResPathArgs = rPath.flatMap(p => Seq("-S", p.absolutePath))

    val aapt = Seq(apPath.absolutePath, "package", "--auto-add-overlay", "-f",
        "-M", manPath.head.absolutePath,
        "-A", assetPath.absolutePath,
        "-I", jPath.absolutePath,
        "-F", resApkPath.absolutePath) ++
        libraryResPathArgs
    s.log.debug("packaging: " + aapt.mkString(" "))
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
    (proguardConfiguration, proguardOutputPath, streams) map {
    (proguardConfiguration, proguardOutputPath, streams) =>

      proguardConfiguration map { configFile =>
        // Execute Proguard
        streams.log.info("Executing Proguard with configuration file " + configFile.getAbsolutePath)

        // Parse the configuration
        val config = new ProGuardConfiguration
        val parser = new ConfigurationParser(configFile, new Properties)
        parser.parse(config)

        // Execute ProGuard
        val proguard = new ProGuard(config)
        proguard.execute

        // Return the proguard-ed output JAR
        proguardOutputPath
      }
  }

  private def proguardConfigurationTask: Project.Initialize[Task[Option[File]]] =
    (useProguard, proguardOptimizations, classDirectory,
    generatedProguardConfigPath, includedClasspath, providedClasspath,
    proguardOutputPath, manifestPackage, proguardOptions, sourceManaged) map {

    (useProguard, proguardOptimizations, classDirectory,
    genConfig, includedClasspath, providedClasspath,
    proguardOutputPath, manifestPackage, proguardOptions, sourceManaged) =>

      if (useProguard) {

          val generatedOptions =
            if(genConfig.exists())
              scala.io.Source.fromFile(genConfig).getLines.filterNot(x => x.isEmpty || x.head == '#').toSeq
            else Seq()

          val optimizationOptions = if (proguardOptimizations.isEmpty) Seq("-dontoptimize") else proguardOptimizations
          val manifestr = List("!META-INF/MANIFEST.MF", "R.class", "R$*.class",
                               "TR.class", "TR$.class", "library.properties")
          val sep = JFile.pathSeparator

          // Input class files
          val inClass = "\"" + classDirectory.absolutePath + "\""

          // Input library JARs to be included in the APK
          val inJars = includedClasspath
                       .map("\"" +_ + "\"" + manifestr.mkString("(", ",!**/", ")"))
                       .mkString(sep)

          // Input library JARs to be provided at runtime
          val inLibrary = providedClasspath
                          .map("\"" + _.absolutePath + "\"")
                          .mkString(sep)

          // Output JAR
          val outJar = "\""+proguardOutputPath.absolutePath+"\""

          // Proguard arguments
          val args = (
                 "-injars" :: inClass ::
                 "-injars" :: inJars ::
                 "-outjars" :: outJar ::
                 "-libraryjars" :: inLibrary ::
                 Nil) ++
                 generatedOptions ++
                 optimizationOptions ++ (
                 "-dontwarn" :: "-dontobfuscate" ::
                 "-dontnote scala.Enumeration" ::
                 "-dontnote org.xml.sax.EntityResolver" ::
                 "-keep public class * extends android.app.backup.BackupAgent" ::
                 "-keep public class * extends android.appwidget.AppWidgetProvider" ::
                 "-keep class scala.collection.SeqLike { public java.lang.String toString(); }" ::
                 "-keep class scala.reflect.ScalaSignature" ::
                 "-keep public class " + manifestPackage + ".** { public protected *; }" ::
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

          // Write that to a file
          val configFile = sourceManaged / "proguard.txt"
          val writer = new ConfigurationWriter(configFile)
          writer.write(config)
          writer.close

          // Return the configuration file
          Some(configFile)

        } else None
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

    // Proguard configuration
    proguardConfiguration <<= proguardConfigurationTask,

    // Final APK generation
    packageConfig <<=
      (toolsPath, packageApkPath, resourcesApkPath, dxOutputPath,
       nativeLibrariesPath, managedNativePath, dxInputs, resourceDirectory) map
      (ApkConfig(_, _, _, _, _, _, _, _)),

    apk <<= apkTask dependsOn (cleanApk, aaptPackage, copyNativeLibraries),

    // Package installation
    install <<= installTask dependsOn apk,

    // Package uninstallation
    uninstall <<= uninstallTask
  )
}
