import sbt._

import Keys._
import AndroidKeys._
import AndroidHelpers._

object AndroidLaunch {

  private def startTask(emulator: Boolean) =
    (dbPath, manifestSchema, manifestPackage, manifestPath) map {
      (dp, schema, mPackage, amPath) =>
      adbTask(dp.absolutePath,
              emulator,
              "shell am start -a android.intent.action.MAIN -n "+mPackage+"/"+
              launcherActivity(schema, amPath, mPackage))
  }

  private def launcherActivity(schema: String, amPath: File, mPackage: String) = {
    val launcher = for (
         activity <- (manifest(amPath) \\ "activity");
         action <- (activity \\ "action");
         val name = action.attribute(schema, "name").getOrElse(error{
            "action name not defined"
          }).text;
         if name == "android.intent.action.MAIN"
    ) yield {
      val act = activity.attribute(schema, "name").getOrElse(error("activity name not defined")).text
      if (act.contains(".")) act else mPackage+"."+act
    }
    launcher.headOption.getOrElse("")
  }

  lazy val settings: Seq[Setting[_]] =
    AndroidInstall.settings ++
    inConfig(Android) (Seq (
      startDevice <<= startTask(false),
      startEmulator <<= startTask(true),

      startDevice <<= startDevice dependsOn installDevice,
      startEmulator <<= startEmulator dependsOn installEmulator
    ))
}
