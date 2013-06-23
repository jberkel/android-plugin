package sbtandroid

import sbt._
import Keys._
import AndroidHelpers._

/**
 * Android target base trait
 */
trait AndroidTarget {
  /**
   * Target-specific options send to ADB
   */
  val options: Seq[String]

  /**
   * Runs ADB commands for the given adb executable path.
   */
  def apply(adbPath: File, extra: String*) = {
    // Full command line
    val command = Seq(adbPath.absolutePath) ++ options ++ extra

    // Output buffer
    val output = new StringBuffer

    // Run the command and grab the exit value
    val exit = command.run(new ProcessIO(
      in => (),
      out => output.append(IO.readStream(out)),
      err => output.append(IO.readStream(err)),
      inheritedInput => false
    )).exitValue

    // Return the output and exit code
    (exit, output.toString)
  }

  /**
   * Returns a task that simply runs the specified ADB command, and sends the
   * output to the SBT logs.
   */
  def run(adbPath: File, s: TaskStreams, extra: String*) = {
    // Display the command in the debug logs
    s.log.debug((Seq(adbPath.absolutePath) ++ options ++ extra).mkString(" "))

    // Run the command
    val (exit, output) = this(adbPath, extra: _*)

    // Display the error and fail on ADB failure
    if (exit != 0 || output.contains("Failure")) {
      s.log.error(output)
      sys.error("Error executing ADB")

    // If the command succeeded, log the output to the debug stream
    } else s.log.debug(output)

    // Return the output
    output
  }

  /**
   * Starts the app on the target
   */
  def startApp(
    adbPath: File,
    s: TaskStreams,
    manifestSchema: String,
    manifestPackage: String,
    manifestPath: Seq[java.io.File]) = {

    // Target activity (defined in the manifest)
    val activity = launcherActivity(
      manifestSchema,
      manifestPath.head,
      manifestPackage)

    // Full intent target
    val intentTarget = manifestPackage + "/" + activity

    // Run the command
    run(adbPath, s,
      "shell", "am", "start",
      "-a", "android.intent.action.MAIN",
      "-n", intentTarget
    )
  }

  /**
   * Runs instrumentation tests on an app
   */
  def testApp(
    adbPath: File,
    manifestPackage: String,
    testRunner: String,
    testName: Option[String] = None) = {

    // Full intent target
    val intentTarget = manifestPackage + "/" + testRunner

    // Run the command
    testName match {
      case Some(test) =>
        this(adbPath,
          "shell", "am", "instrument",
          "-r",
          "-e", "class", test,
          "-w", intentTarget)

      case None =>
        this(adbPath,
          "shell", "am", "instrument",
          "-r",
          "-w", intentTarget)
    }
  }

  /**
   * Installs or uninstalls a package on the target
   */
   def installPackage(adbPath: File, streams: TaskStreams, apkPath: File) =
     run(adbPath, streams, "install", "-r", apkPath.absolutePath)
   def uninstallPackage(adbPath: File, streams: TaskStreams, packageName: String) =
     run(adbPath, streams, "uninstall", packageName)
}

/**
 * Some common Android target definitions
 */
object AndroidDefaultTargets {

  /**
   * Selects a connected Android target
   */
  object Auto extends AndroidTarget {
    val options = Seq.empty
  }

  /**
   * Selects a connected Android device
   */
  object Device extends AndroidTarget {
    val options = Seq("-d")
  }

  /**
   * Selects a connected Android emulator
   */
  object Emulator extends AndroidTarget {
    val options = Seq("-e")
  }

  /**
   * Selects any Android device or emulator matching the given UID
   */
  case class UID(val uid: String) extends AndroidTarget {
    val options = Seq("-s", uid)
  }
}
