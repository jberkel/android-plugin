package org.scalasbt.androidplugin

import sbt._
import Keys._
import AndroidPlugin._
import AndroidHelpers._

object AndroidPreload {

  private def jarName(lib: File, ver: String) =
    lib.getName.replace(".jar", "-" + ver + ".jar")

  private def deviceJarPath(lib: File, ver: String) =
    "/system/framework/" + jarName(lib,ver)

  private def devicePermissionPath(ver: String) =
    "/system/etc/permissions/scala-library-" + ver + ".xml"

  private def deviceDesignation(implicit emulator: Boolean) =
    if (emulator) "emulator" else "device"

  /****************
   * State checks *
   ****************/

  private def checkFileExists (db: File, s: TaskStreams, filename: String)(implicit emulator: Boolean) = {

    // Run the `ls` command on the device/emulator
    val flist = adbTask(db.absolutePath, emulator, s,
      "shell", "ls", filename, "2>/dev/null")

    // Check if we found the file
    val found = flist.contains(filename)

    // Inform the user
    s.log.debug ("File " + filename +
      (if (found) " found on " else " does not exist on ") + deviceDesignation)

    // Return `true` if the file has been found
    found
  }

  private def checkPreloadedScalaVersion (db: File, si: ScalaInstance, s: TaskStreams)(implicit emulator: Boolean) = {
    import scala.xml._

    // Retrieve the contents of the `scala_library` permission file
    val permissions = adbTask(db.absolutePath, emulator, s,
      "shell", "cat /system/etc/permissions/scala-library-" + si.version + ".xml")

    // Parse the library file
    val preloadedScalaFile = (
      try { Some(XML.loadString(permissions) \\ "permissions" \\ "library" \\ "@file") }
      catch { case _ => None }

    // Convert the XML node to a String
    ).map(_.text)

    // Check if this is the right library version
    .filter(_ == deviceJarPath(si.libraryJar, si.version))

    // Check if the library is present
    .filter(checkFileExists(db, s, _))

    // Inform the user
    preloadedScalaFile match {
      case Some(f) =>
        s.log.info("Scala " + si.version + " is preloaded on the " + deviceDesignation + " (" + f + ")")
      case None => ()
    }

    // Return the library name
    preloadedScalaFile
  }

  /****************************
   * Scala preloading process *
   ****************************/

  private def doPreloadPermissions(
    db: File, si: ScalaInstance, s: TaskStreams)(implicit emulator: Boolean): Unit = {

    // Inform the user
    s.log.info("Setting permissions for "
      + jarName(si.libraryJar, si.version))

    // Create the contents of the file
    val xmlContent =
      <permissions>
        <library
        name={{ "scala-library-" + si.version }}
        file={{ deviceJarPath(si.libraryJar, si.version) }} />
      </permissions>

    // Generate string from the XML
    val xmlString = scala.xml.Utility.toXML(
      scala.xml.Utility.trim(xmlContent),
      minimizeTags=true
    ).toString.replace("\"", "\\\"")

    // Load the file on the device
    adbTask (db.absolutePath, emulator, s,
      "shell", "echo", xmlString,
      ">", devicePermissionPath(si.version)
    )
  }

  private def doPreloadJar(
    db: File, dx: File, target: File, si: ScalaInstance, s: TaskStreams)(implicit emulator: Boolean): Unit = {

    // This is the temporary JAR path
    val name = jarName(si.libraryJar, si.version)
    val tempJarPath = (target / name)

    // Dex current Scala library if necessary
    if (tempJarPath.lastModified < si.libraryJar.lastModified) {
      val dxCmd = Seq(dx.absolutePath,
        "-JXmx1024M",
        "-JXms1024M",
        "-JXss4M",
        "--no-optimize",
        "--debug",
        "--dex",
        "--output=" + tempJarPath.getAbsolutePath,
        si.libraryJar.getAbsolutePath
      )
      s.log.info  ("Dexing Scala library")
      s.log.debug (dxCmd.!!)
    }

    // Load the file on the device
    s.log.info("Installing " + name)
    adbTask (db.absolutePath, emulator, s,
      "push",
      tempJarPath.getAbsolutePath,
      deviceJarPath(si.libraryJar, si.version)
    )
  }

  private def doReboot (db: File, s: TaskStreams)(implicit emulator: Boolean) = {
      s.log.info("Rebooting " + deviceDesignation)
      if (emulator) adbTask(db.absolutePath, emulator, s, "emu", "kill")
      else adbTask(db.absolutePath, emulator, s, "reboot")
      ()
  }

  private def doRemountReadWrite (db: File, s: TaskStreams)(implicit emulator: Boolean) = {
    s.log.info("Remounting /system as read-write")
    adbTask(db.absolutePath, emulator, s, "root")
    adbTask(db.absolutePath, emulator, s, "wait-for-device")
    adbTask(db.absolutePath, emulator, s, "remount")
  }

  /***************************
   * Emulator-specific stuff *
   ***************************/

  private def doStartEmuReadWrite (db: File, s: TaskStreams,
    sdkPath: File, toolsPath: File, avdName: String, verbose: Boolean) = {

    // Find emulator config path
    val avdPath = Path.userHome / ".android" / "avd" / (avdName + ".avd")

    // Open config.ini
    val configFile = avdPath / "config.ini"

    // Read the contents and split by newline
    val configContents = scala.io.Source.fromFile(configFile).mkString

    // Regexp to match the system dir
    val sysre = "image.sysdir.1 *= *(.*)".r 

    sysre findFirstIn configContents match {
      case Some(sysre(sys)) =>

        // Copy system image to the emulator directory if needed
        val rosystem = sdkPath / sys / "system.img"
        val rwsystem = avdPath / "system.img"
        if (!rwsystem.exists) {
          s.log.info("Copying system image")
          "cp %s %s".format(rosystem.getAbsolutePath, rwsystem.getAbsolutePath).!
        }

        // Start the emulator with the local persistent system image
        s.log.info("Starting emulator with read-write system")
        s.log.info("This may take a while...")

        val rwemuCmdF = "%s/emulator -avd %s -no-boot-anim -no-snapshot -qemu -nand system,size=0x1f400000,file=%s -nographic -monitor null"
        val rwemuCmdV = "%s/emulator -avd %s -no-boot-anim -no-snapshot -verbose -qemu -nand system,size=0x1f400000,file=%s -nographic -show-kernel -monitor null"
        val rwemuCmd = (if (!verbose) rwemuCmdF else rwemuCmdV)
          .format(toolsPath, avdName, (avdPath / "system.img").getAbsolutePath)

        s.log.debug (rwemuCmd)
        rwemuCmd.run

        // Remount system as read-write
        adbTask(db.absolutePath, true, s, "wait-for-device")
        adbTask(db.absolutePath, true, s, "root")
        adbTask(db.absolutePath, true, s, "wait-for-device")
        adbTask(db.absolutePath, true, s, "remount")

      case None => throw new Exception("Unable to find the system image")
    }
  }

  private def doKillEmu (db: File, s: TaskStreams)(implicit emulator: Boolean) = {
      if (emulator)
        adbTaskWithOutput(db.absolutePath, emulator, s, "emu", "kill")
      ()
  }


  /*******************************
   * Tasks related to preloading *
   *******************************/

  private def preloadDeviceTask =
    (dbPath, dxPath, target, scalaInstance, streams) map {
    (dbPath, dxPath, target, scalaInstance, streams) =>

      // We're not using the emulator
      implicit val emulator = false

      // Wait for the device
      adbTask(dbPath.absolutePath, emulator, streams, "wait-for-device")

      // Check if the Scala library is already prelaoded
      checkPreloadedScalaVersion(dbPath, scalaInstance, streams) match {

        // Don't do anything if the library is preloaded
        case Some(_) => ()

        // Preload the Scala library
        case None =>
          // Remount the device in read-write mode
          doRemountReadWrite (dbPath, streams)

          // Push files to the device
          doPreloadJar         (dbPath, dxPath, target, scalaInstance, streams)
          doPreloadPermissions (dbPath, scalaInstance, streams)

          // Reboot / Kill emulator
          doReboot (dbPath, streams); ()
      }
    }

  private def preloadEmulatorTask(emulatorName: TaskKey[String]) =
    (emulatorName, toolsPath, sdkPath, dbPath, dxPath, target, scalaInstance, streams) map {
    (emulatorName, toolsPath, sdkPath, dbPath, dxPath, target, scalaInstance, streams) =>

      // We're using the emulator
      implicit val emulator = true

      // Kill any running emulator
      doKillEmu (dbPath, streams)

      // Restart the emulator in system read-write mode
      doStartEmuReadWrite (dbPath, streams, sdkPath, toolsPath, emulatorName, false)

      // Push files to the device
      doPreloadJar         (dbPath, dxPath, target, scalaInstance, streams)
      doPreloadPermissions (dbPath, scalaInstance, streams)

      // Reboot / Kill emulator
      doKillEmu (dbPath, streams); ()
    }

  private def commandTask(command: String)(implicit emulator: Boolean) =
    (dbPath, streams) map {
      (d,s) => adbTask(d.absolutePath, emulator, s, command)
      ()
    }

  private def unloadDeviceTask =
    (dbPath, scalaInstance, streams) map {
      (d,si,s) =>
        implicit val emulator = false
        adbTask(d.absolutePath, emulator, s, "root")
        adbTask(d.absolutePath, emulator, s, "wait-for-device")
        adbTask(d.absolutePath, emulator, s, "remount")
        adbTask(d.absolutePath, emulator, s, "wait-for-device")
        adbTask(d.absolutePath, emulator, s, "shell", "rm", deviceJarPath(si.libraryJar, si.version))
        adbTask(d.absolutePath, emulator, s, "shell", "rm", devicePermissionPath(si.version))
        s.log.info("Scala has been removed from the " + deviceDesignation)
    }

  private def unloadEmulatorTask(emulatorName: TaskKey[String]) =
    (emulatorName, toolsPath, sdkPath, dbPath, dxPath, target, scalaInstance, streams) map {
    (emulatorName, toolsPath, sdkPath, d, dxPath, target, si, s) =>

        // We're using the emulator
        implicit val emulator = true

        // Kill any running emulator
        doKillEmu (d, s)

        // Restart the emulator in system read-write mode
        doStartEmuReadWrite (d, s, sdkPath, toolsPath, emulatorName, false)

        // Remove the scala libs
        adbTask(d.absolutePath, emulator, s, "wait-for-device")
        adbTask(d.absolutePath, emulator, s, "root")
        adbTask(d.absolutePath, emulator, s, "wait-for-device")
        adbTask(d.absolutePath, emulator, s, "remount")
        adbTask(d.absolutePath, emulator, s, "wait-for-device")
        adbTask(d.absolutePath, emulator, s, "shell", "rm", deviceJarPath(si.libraryJar, si.version))
        adbTask(d.absolutePath, emulator, s, "shell", "rm", devicePermissionPath(si.version))
        doKillEmu (d, s)

        s.log.info("Scala has been removed from the " + deviceDesignation)
    }

  /*************************
   * Insert tasks into SBT *
   *************************/

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq(
    // Preload Scala on the device/emulator
    preloadDevice <<= preloadDeviceTask,
    preloadEmulator <<= InputTask(
      (sdkPath)(AndroidEmulator.installedAvds(_)))(preloadEmulatorTask),

    // Uninstall previously preloaded Scala
    unloadDevice <<= unloadDeviceTask,
    unloadEmulator <<= InputTask(
      (sdkPath)(AndroidEmulator.installedAvds(_)))(unloadEmulatorTask)
  ))
}
