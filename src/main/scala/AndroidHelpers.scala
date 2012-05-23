import sbt._
import Keys._

import AndroidKeys._

object AndroidHelpers {

  def directory(path: SettingKey[File]) = path map (IO.createDirectory(_))

  def determineAndroidSdkPath(es: Seq[String]) = {
    val paths = for ( e <- es; p = System.getenv(e); if p != null) yield p
    if (paths.isEmpty) None else Some(Path(paths.head).asFile)
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

  def adbTask(dPath: String, emulator: Boolean, s: TaskStreams, action: String*) {
    val (exit, out) = adbTaskWithOutput(dPath, emulator, s, action:_*)
    if (exit != 0 ||
        // adb doesn't bother returning a non-zero exit code on failure
        out.toString.contains("Failure")) {
      s.log.error(out.toString)
      sys.error("error executing adb")
    } else s.log.info(out.toString)
  }

  def adbTaskWithOutput(dPath: String, emulator: Boolean, s: TaskStreams, action: String*) = {
    val adb = Seq(dPath, if (emulator) "-e" else "-d") ++ action
    s.log.debug(adb.mkString(" "))
    val out = new StringBuffer
    val exit = adb.run(new ProcessIO(input => (),
                          output => out.append(IO.readStream(output)),
                          error  => out.append(IO.readStream(error)))
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
