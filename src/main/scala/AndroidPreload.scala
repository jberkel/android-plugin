import sbt._
import Keys._
import AndroidKeys._
import AndroidHelpers._

object AndroidPreload {

  private def jarName(lib: File, ver: String) =
    lib.getName.replace(".jar", "." + ver + ".jar")

  private def deviceJarPath(lib: File, ver: String) =
    "/system/framework/" + jarName(lib,ver)

  private def deviceDesignation(implicit emulator: Boolean) =
    if (emulator) "emulator" else "device"

  private def deviceTask[T]
    (task_device: TaskKey[T], task_emulator: TaskKey[T])(implicit emulator: Boolean) =
    if (emulator) task_emulator else task_device

  private def preloaded(implicit emulator: Boolean) =
    deviceTask(preloadedDevice, preloadedEmulator)

  /****************
   * State checks *
   ****************/

  private def checkFileExists (db: File, s: TaskStreams, filename: String)(implicit emulator: Boolean) = {

    // Run the `ls` command on the device/emulator
    val fileR = adbTaskWithOutput(db.absolutePath, emulator, s,
      "shell", "ls", filename, "2>/dev/null")
    val fileE = fileR._1
    val fileS = fileR._2

    // Check if we found the file
    val found = fileE == 0 && fileS.contains(filename)

    // Inform the user
    s.log.debug ("File " + filename +
      (if (found) " found on " else " does not exist on ") + deviceDesignation)

    // Return `true` if the file has been found
    found
  }

  private def checkPreloadedScalaVersion (db: File, si: ScalaInstance, s: TaskStreams)(implicit emulator: Boolean) = {
    import scala.xml._

    // Wait for the device
    doWaitForDevice(db, s)

    // Retrieve the contents of the `scala_library` permission file
    val permissions = adbTaskWithOutput(db.absolutePath, emulator, s,
      "shell", "cat /system/etc/permissions/scala_library.xml")

    // Parse the library file
    val preloadedScalaFile = (
      try { Some(XML.loadString(permissions._2) \\ "permissions" \\ "library" \\ "@file") }
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

  private def doPreloadPermissions (
    db: File, si: ScalaInstance, s: TaskStreams)(implicit emulator: Boolean) = {

    // Inform the user
    s.log.info("Setting permissions for "
      + jarName(si.libraryJar, si.version))

    // Create the contents of the file
    val xmlContent =
      <permissions>
        <library
        name="scala_library"
        file={{ deviceJarPath(si.libraryJar, si.version) }} />
      </permissions>

    // Generate string from the XML
    val xmlString = scala.xml.Utility.toXML(
      scala.xml.Utility.trim(xmlContent),
      minimizeTags=true
    ).toString.replace("\"", "\\\"")

    // Load the file on the device
    adbTaskWithOutput (db.absolutePath, emulator, s,
      "shell", "echo", xmlString,
      ">", "/system/etc/permissions/scala_library.xml"

    // Return true on success
    )._1 == 0
  }

  private def doPreloadJar (
    db: File, dx: File, target: File, si: ScalaInstance, s: TaskStreams)(implicit emulator: Boolean) = {

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
    adbTaskWithOutput (db.absolutePath, emulator, s,
      "push",
      tempJarPath.getAbsolutePath,
      deviceJarPath(si.libraryJar, si.version)

    // Return true on success
    )._1 == 0
  }

  private def doWaitForDevice (db: File, s: TaskStreams)(implicit emulator: Boolean) = {
    s.log.info("Waiting for " + deviceDesignation)
    adbTaskWithOutput(db.absolutePath, emulator, s, "wait-for-device")
    ()
  }

  private def doReboot (db: File, s: TaskStreams)(implicit emulator: Boolean) = {
    s.log.info("Rebooting " + deviceDesignation)
    adbTaskWithOutput(db.absolutePath, emulator, s, "reboot")
    ()
  }

  /*******************************
   * Tasks related to preloading *
   *******************************/

  private def preloadedTask(implicit emulator: Boolean) =
    (dbPath, scalaInstance, streams) map (checkPreloadedScalaVersion _)

  private def preloadTask(implicit emulator: Boolean) =
    (preloaded, dbPath, dxPath, target, scalaInstance, streams) map {
    (preloaded, dbPath, dxPath, target, scalaInstance, streams) =>

      preloaded match {
        // Don't do anything if the library is preloaded
        case Some(_) => ()

        // Preload the Scala library
        case None =>
          // Push files to the device
          if (
            doPreloadJar         (dbPath, dxPath, target, scalaInstance, streams) &&
            doPreloadPermissions (dbPath, scalaInstance, streams)

          // Reboot once this is done
          ) doReboot(dbPath, streams)
      }
    }

  private def commandTask(command: String)(implicit emulator: Boolean) =
    (dbPath, streams) map {
      (d,s) => adbTaskWithOutput(d.absolutePath, emulator, s, command)
      ()
    }

  private def unloadTask(implicit emulator: Boolean) =
    (dbPath, scalaInstance, streams) map {
      (d,si,s) =>
        adbTaskWithOutput(d.absolutePath, emulator, s, "shell", "rm", deviceJarPath(si.libraryJar, si.version))
        adbTaskWithOutput(d.absolutePath, emulator, s, "shell", "rm", "/system/etc/permissions/scala_library.xml")
        s.log.info("Scala has been removed from the " + deviceDesignation)
    }

  /*************************
   * Insert tasks into SBT *
   *************************/

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq(
    // Device rooting/remounting
    rootDevice <<= commandTask("root")(false),
    rootEmulator <<= commandTask("root")(true),
    remountDevice <<= commandTask("remount")(false),
    remountDevice <<= remountDevice dependsOn (rootDevice),
    remountEmulator <<= commandTask("remount")(true),
    remountEmulator <<= remountEmulator dependsOn (rootEmulator),

    // State checks
    preloadedDevice <<= preloadedTask(false) dependsOn (rootDevice),
    preloadedEmulator <<= preloadedTask(true) dependsOn (rootEmulator),

    // Subtasks related to Scala preloading
    preloadDevice <<= preloadTask(false) dependsOn (remountDevice),
    preloadEmulator <<= preloadTask(true) dependsOn (remountEmulator),

    // Uninstall previously preloaded Scala
    unloadDevice <<= unloadTask(false) dependsOn(remountDevice),
    unloadEmulator <<= unloadTask(true) dependsOn(remountEmulator)
  ))
}
