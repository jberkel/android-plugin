package org.scalasbt.androidplugin

import sbt._
import Keys._

import AndroidPlugin._

object AndroidHelpers {

  def directory(path: SettingKey[File]) = path map (IO.createDirectory(_))

  /**
   * Finds out where the Android SDK is located on your system, based on :
   *   * Environment variables
   *   * The local.properties files
   */
  def determineAndroidSdkPath(envs: Seq[String], dir: File): File = {
    // Try to find the SDK path in the default environment variables
    val paths = for ( e <- envs; p = System.getenv(e); if p != null) yield p
    if (!paths.isEmpty) Path(paths.head).asFile

    // If not found, try to read the `local.properties` file
    else {
      val local = new File(dir, "local.properties")
      if (local.exists()) {
        (for (sdkDir <- (for (l <- IO.readLines(local);
             if (l.startsWith("sdk.dir")))
             yield l.substring(l.indexOf('=')+1)))
             yield new File(sdkDir)).headOption.getOrElse(
              sys.error("local.properties did not contain sdk.dir")
             )

      // If nothing is found either, display an error
      } else {
        sys.error(
          "Android SDK not found. You might need to set %s".format(envs.mkString(" or "))
        )
      }
    }
  }

  def isWindows = System.getProperty("os.name").startsWith("Windows")
  def osBatchSuffix = if (isWindows) ".bat" else ""

  def dxMemoryParameter(javaOpts: String) = {
    // per http://code.google.com/p/android/issues/detail?id=4217, dx.bat
    // doesn't currently support -JXmx arguments.  For now, omit them in windows.
    if (isWindows) "" else javaOpts
  }

  def usesSdk(mpath: File, schema: String, key: String) =
    (manifest(mpath) \ "uses-sdk").head.attribute(schema, key).map(_.text.toInt)

  def adbTask(dPath: String, emulator: Boolean, s: TaskStreams, action: String*) = {
    val (exit, out) = adbTaskWithOutput(dPath, emulator, s, action:_*)

    // Check exit value
    if (exit != 0 ||
        // adb doesn't bother returning a non-zero exit code on failure
        out.toString.contains("Failure")) {
      s.log.error(out.toString)
      sys.error("error executing adb")
    } else s.log.debug(out.toString)

    // Return output
    out.toString
  }

  def adbTaskWithOutput(dPath: String, emulator: Boolean, s: TaskStreams, action: String*) = {
    val adb = Seq(dPath, if (emulator) "-e" else "-d") ++ action
    s.log.debug(adb.mkString(" "))
    val out = new StringBuffer
    val exit = adb.run(new ProcessIO(input => (),
                          output => out.append(IO.readStream(output)),
                          error  => out.append(IO.readStream(error)),
                          inheritedInput => false)
                      ).exitValue()
    (exit, out.toString)
  }

  def startTask(emulator: Boolean) =
    (dbPath, manifestSchema, manifestPackage, manifestPath, streams) map {
      (dp, schema, mPackage, amPath, s) =>
      adbTask(dp.absolutePath,
              emulator, s,
              "shell", "am", "start", "-a", "android.intent.action.MAIN",
              "-n", mPackage+"/"+
              launcherActivity(schema, amPath.head, mPackage))
  }

  def launcherActivity(schema: String, amPath: File, mPackage: String) = {
    val launcher = for (
         activity <- (manifest(amPath) \\ "activity");
         action <- (activity \\ "action");
         val name = action.attribute(schema, "name").getOrElse(sys.error{
            "action name not defined"
          }).text;
         if name == "android.intent.action.MAIN"
    ) yield {
      val act = activity.attribute(schema, "name").getOrElse(sys.error("activity name not defined")).text
      if (act.contains(".")) act else mPackage+"."+act
    }
    launcher.headOption.getOrElse("")
  }

  def manifest(mpath: File) = xml.XML.loadFile(mpath)

}
