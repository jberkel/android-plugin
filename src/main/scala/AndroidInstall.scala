import proguard.{Configuration=>ProGuardConfiguration, ProGuard, ConfigurationParser}

import sbt._
import Keys._
import AndroidKeys._
import AndroidHelpers._

import java.io.{File => JFile}

object AndroidInstall {

  private def installTask(emulator: Boolean) = (dbPath, packageApkPath, streams) map { (dp, p, s) =>
    adbTask(dp.absolutePath, emulator, s, "install", "-r ", p.absolutePath)
  }

  private def uninstallTask(emulator: Boolean) = (dbPath, manifestPackage, streams) map { (dp, m, s) =>
    adbTask(dp.absolutePath, emulator, s, "uninstall", m)
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
    (dxPath, dxInputs, dxOpts, proguardOptimizations, classDirectory, classesDexPath, scalaInstance, streams) map {
    (dxPath, dxInputs, dxOpts, proguardOptimizations, classDirectory, classesDexPath, scalaInstance, streams) =>

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
                          dxMemoryParameter(dxOpts._1),
                          "--dex", noLocals,
                          "--num-threads="+java.lang.Runtime.getRuntime.availableProcessors,
                          "--output="+output.getAbsolutePath) ++
                          inputs.map(_.absolutePath)).filter(_.length > 0)
          streams.log.debug(dxCmd.mkString(" "))
          streams.log.info("Dexing "+output.getAbsolutePath)
          streams.log.debug(dxCmd !!)
        } else streams.log.debug("dex file " + output.getAbsolutePath + " uptodate, skipping")
      }

      // Option[Seq[String]]
      // - None standard dexing for prodaction stage
      // - Some(Seq(predex_library_regexp)) predex only changed libraries for development stage
      dxOpts._2 match {
        case None =>
          dexing(dxInputs.get, classesDexPath)
        case Some(predex) =>
          val (dexFiles, predexFiles) = predex match {
            case exceptSeq: Seq[_] if exceptSeq.nonEmpty =>
              val (filtered, orig) = dxInputs.get.partition(file =>
              exceptSeq.exists(filter => {
                streams.log.debug("apply filter \"" + filter + "\" to \"" + file.getAbsolutePath + "\"")
                file.getAbsolutePath.matches(filter)
              }))
              // dex only classes directory ++ filtered, predex all other
              ((classDirectory --- scalaInstance.libraryJar).get ++ filtered, orig)
            case _ =>
              // dex only classes directory, predex all other
              ((classDirectory --- scalaInstance.libraryJar).get, (dxInputs --- classDirectory).get)
          }
          dexFiles.foreach(s => streams.log.debug("pack in dex \"" + s.getName + "\""))
          predexFiles.foreach(s => streams.log.debug("pack in predex \"" + s.getName + "\""))
          // dex
          dexing(dexFiles, classesDexPath)
          // predex
          predexFiles.get.foreach(f => {
            val predexPath = new JFile(classesDexPath.getParent, "predex")
            if (!predexPath.exists)
              predexPath.mkdir
            val output = new File(predexPath, f.getName)
            val outputPermissionDescriptor = new File(predexPath, f.getName.replaceFirst(".jar$", ".xml"))
            dexing(Seq(f), output)
            val permission = <permissions><library name={ f.getName.replaceFirst(".jar$", "") } file={ "/data/" + f.getName } /></permissions>
            val p = new java.io.PrintWriter(outputPermissionDescriptor)
            try { p.println(permission) } finally { p.close() }
          })
      }

      classesDexPath
    }

  private def proguardTask: Project.Initialize[Task[Option[File]]] =
    (useProguard, proguardOptimizations, classDirectory, proguardInJars, streams,
     classesMinJarPath, libraryJarPath, manifestPackage, proguardOption) map {
    (useProguard, proguardOptimizations, classDirectory, proguardInJars, streams,
     classesMinJarPath, libraryJarPath, manifestPackage, proguardOption) =>
      if (useProguard) {
          val optimizationOptions = if (proguardOptimizations.isEmpty) Seq("-dontoptimize") else proguardOptimizations
          val manifestr = List("!META-INF/MANIFEST.MF", "R.class", "R$*.class",
                               "TR.class", "TR$.class", "library.properties")
          val sep = JFile.pathSeparator
          val inJars = ("\"" + classDirectory.absolutePath + "\"") +:
                       proguardInJars.map("\""+_+"\""+manifestr.mkString("(", ",!**/", ")"))

          val args = (
                 "-injars" :: inJars.mkString(sep) ::
                 "-outjars" :: "\""+classesMinJarPath.absolutePath+"\"" ::
                 "-libraryjars" :: libraryJarPath.map("\""+_+"\"").mkString(sep) ::
                 Nil) ++
                 optimizationOptions ++ (
                 "-dontwarn" :: "-dontobfuscate" ::
                 "-dontnote scala.Enumeration" ::
                 "-dontnote org.xml.sax.EntityResolver" ::
                 "-keep public class * extends android.app.Activity" ::
                 "-keep public class * extends android.app.Service" ::
                 "-keep public class * extends android.app.backup.BackupAgent" ::
                 "-keep public class * extends android.appwidget.AppWidgetProvider" ::
                 "-keep public class * extends android.content.BroadcastReceiver" ::
                 "-keep public class * extends android.content.ContentProvider" ::
                 "-keep public class * extends android.view.View" ::
                 "-keep public class * extends android.app.Application" ::
                 "-keep public class * extends android.app.Instrumentation" ::
                 "-keep public class * extends android.test.InstrumentationTestRunner" ::
                 "-keep public class "+manifestPackage+".** { public protected *; }" ::
                 "-keep public class * implements junit.framework.Test { public void test*(); }" ::
                 """
                  -keepclassmembers class * implements java.io.Serializable {
                    private static final java.io.ObjectStreamField[] serialPersistentFields;
                    private void writeObject(java.io.ObjectOutputStream);
                    private void readObject(java.io.ObjectInputStream);
                    java.lang.Object writeReplace();
                    java.lang.Object readResolve();
                   }
                   """ ::
                 proguardOption :: Nil )
          val config = new ProGuardConfiguration
          new ConfigurationParser(args.toArray[String]).parse(config)
          streams.log.debug("executing proguard: "+args.mkString("\n"))
          new ProGuard(config).execute
          Some(classesMinJarPath)
      } else {
          streams.log.info("Skipping Proguard")
          None
      }
    }

  private def packageTask(debug: Boolean):Project.Initialize[Task[File]] = (packageConfig, streams) map { (c, s) =>
    val builder = new ApkBuilder(c, debug)
    builder.build.fold(sys.error(_), s.log.info(_))
    s.log.debug(builder.outputStream.toString)
    c.packageApkPath
  }

  lazy val installerTasks = Seq (
    installEmulator <<= installTask(emulator = true) dependsOn packageDebug,
    installDevice <<= installTask(emulator = false) dependsOn packageDebug
  )

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (installerTasks ++ Seq (
    uninstallEmulator <<= uninstallTask(emulator = true),
    uninstallDevice <<= uninstallTask(emulator = false),

    makeAssetPath <<= directory(mainAssetsPath),

    aaptPackage <<= aaptPackageTask,
    aaptPackage <<= aaptPackage dependsOn (makeAssetPath, dx),
    dx <<= dxTask,
    dxInputs <<= (proguard, proguardInJars, scalaInstance, classDirectory) map {
      (proguard, proguardInJars, scalaInstance, classDirectory) =>
      proguard match {
         case Some(file) => Seq(file)
         case None => (classDirectory +++ proguardInJars --- scalaInstance.libraryJar) get
      }
    },

    cleanApk <<= (packageApkPath) map (IO.delete(_)),

    proguard <<= proguardTask,
    proguard <<= proguard dependsOn (compile in Compile),

    packageConfig <<=
      (toolsPath, packageApkPath, resourcesApkPath, classesDexPath,
       nativeLibrariesPath, managedNativePath, dxInputs, resourceDirectory) map
      (ApkConfig(_, _, _, _, _, _, _, _)),

    packageDebug <<= packageTask(true),
    packageRelease <<= packageTask(false)
  ) ++ Seq(packageDebug, packageRelease).map {
    t => t <<= t dependsOn (cleanApk, aaptPackage, copyNativeLibraries)
  })
}
