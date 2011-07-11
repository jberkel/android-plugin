import sbt._

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.Log
import com.android.ddmlib.RawImage

import java.io.{File, OutputStream, IOException}
import java.awt.image.{BufferedImage, RenderedImage}
import javax.imageio.ImageIO

import AndroidKeys._

object AndroidHelpers {
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

  def platformName2ApiLevel(pName: String) = pName match {
    case "android-1.0" => 1
    case "android-1.1" => 2
    case "android-1.5" => 3
    case "android-1.6" => 4
    case "android-2.0" => 5
    case "android-2.1" => 7
    case "android-2.2" => 8
    case "android-2.3" => 9
    case "android-2.3.3" => 10
    case "android-3.0" => 11
  }

  def adbTask(dPath: String, emulator: Boolean, action: => String): Unit = 
    Process (<x>
      {dPath} {if (emulator) "-e" else "-d"} {action}
    </x>) !

  def startTask(emulator: Boolean) = 
    (dbPath, manifestSchema, manifestPackage, manifestPath) map { 
      (dp, schema, mPackage, amPath) =>
      adbTask(dp.absolutePath, 
              emulator, 
              "shell am start -a android.intent.action.MAIN -n "+mPackage+"/"+
              launcherActivity(schema, amPath, mPackage))
  }

  def launcherActivity(schema: String, amPath: File, mPackage: String) = {
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

  def manifest(mpath: File) = xml.XML.loadFile(mpath)

  def bridge(path: String) = {
    AndroidDebugBridge.init(false)
    java.lang.Runtime.getRuntime().addShutdownHook(new Thread() { override def run() { AndroidDebugBridge.terminate() }})
    AndroidDebugBridge.createBridge(path, false)
  }

  // ported from http://dustingram.com/wiki/Device_Screenshots_with_the_Android_Debug_Bridge
  def withDevice[F](emulator: Boolean, path: String)(action: IDevice => F):Option[F] = {
    var count = 0
    val bridged = bridge(path)
    while (!bridged.hasInitialDeviceList() && count < 50) {
      Thread.sleep(100)
      count += 1
    }
    if (!bridged.hasInitialDeviceList()) {
      System.err.println("Timeout getting device list")
      None
    } else {
      val (emus, devices) = bridged.getDevices.partition(_.isEmulator)
      (if (emulator) emus else devices).headOption.map(action)
    }
  }

  def screenshot(emulator: Boolean, landscape: Boolean, path: String):Option[Screenshot] = {
    withDevice(emulator, path) { device =>
      val raw = device.getScreenshot()
      val (width2, height2) = if (landscape) (raw.height, raw.width) else (raw.width, raw.height)
      val image = new BufferedImage(width2, height2, BufferedImage.TYPE_INT_RGB)
      var index = 0
      val indexInc = raw.bpp >> 3
      for (y <- 0 until raw.height; x <- 0 until raw.width) {
        val value = raw.getARGB(index)
        if (landscape)
          image.setRGB(y, raw.width - x - 1, value)
        else
          image.setRGB(x, y, value)
        index += indexInc
      }
      new Screenshot(image)
    }
  }
  class Screenshot(val r: RenderedImage) {
    def toFile(format: String, f: File):Boolean = ImageIO.write(r, format, f)
    def toFile(format: String, s: String):Boolean = toFile(format, new File(s))
    def toOutputStream(format: String, o: OutputStream) = ImageIO.write(r, format, o)
  }
}
