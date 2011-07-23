import sbt._
import Keys._

import AndroidKeys._
import AndroidHelpers._

import complete.DefaultParsers._

object AndroidProject extends Plugin {

  val emulatorStart = InputKey[Unit]("emulator-start", 
    "Launches a user specified avd")
  val emulatorStop = TaskKey[Unit]("emulator-stop",
    "Kills the running emulator.")
  val listDevices = TaskKey[Unit]("list-devices",
    "List devices from the adb server.") 

  private def emulatorStartTask = (parsedTask: TaskKey[String]) =>
    (parsedTask, toolsPath) map { (avd, toolsPath) =>
      "%s/emulator -avd %s".format(toolsPath, avd).run
      ()
    }

  private def listDevicesTask: Project.Initialize[Task[Unit]] = (dbPath) map {
    _ +" devices".format(dbPath) !
  }

  private def emulatorStopTask = (dbPath, streams) map { (dbPath, s) =>
    s.log.info("Stopping emulators")
    val serial = "%s -e get-serialno".format(dbPath).!!
    "%s -s %s emu kill".format(dbPath, serial).!
    ()
  }

  val installedAvds = (s: State) => {
    val avds = (Path.userHome / ".android" / "avd" * "*.ini").get
    Space ~> avds.map(f => token(f.base))
                 .reduceLeftOption(_ | _).getOrElse(token("none"))
  }

  lazy val androidSettings: Seq[Setting[_]] = 
    AndroidBase.settings ++
    AndroidLaunch.settings ++
    AndroidDdm.settings

  // Android path and defaults can load for every project
  override lazy val settings: Seq[Setting[_]] = 
    AndroidPath.settings ++ inConfig(Android) (Seq (
      listDevices <<= listDevicesTask,
      emulatorStart <<= InputTask(installedAvds)(emulatorStartTask),
      emulatorStop <<= emulatorStopTask
    )) ++ Seq (
      listDevices <<= (listDevices in Android).identity 
    )
}
