import proguard.{Configuration=>ProGuardConfiguration, ProGuard, ConfigurationParser}
import java.io._
import sbt._
import Process._

trait Startable extends Installable {

  lazy val startDevice = startDeviceAction
  def startDeviceAction = startTask(false) dependsOn(reinstallDevice) describedAs("Start package on device after installation")

  lazy val startEmulator = startEmulatorAction
  def startEmulatorAction = startTask(true) dependsOn(reinstallEmulator) describedAs("Start package on emulator after installation")

  def startTask(emulator: Boolean) = adbTask(emulator, "shell am start -a android.intent.action.MAIN -n "+manifestPackage+"/"+launcherActivity)

  def launcherActivity:String = {
    for (activity <- (manifest \\ "activity")) {
      for(action <- (activity \\ "action")) {
        val name = action.attribute(manifestSchema, "name").getOrElse(error("action name not defined")).text
        if (name == "android.intent.action.MAIN") {
          val act = activity.attribute(manifestSchema, "name").getOrElse(error("activity name not defined")).text
          if (act.isEmpty) error("activity name not defined")
          return if (act.contains(".")) act else manifestPackage+"."+act
        }
      }
    }
    ""
  }
}
