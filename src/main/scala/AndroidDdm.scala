import sbt._
import Keys._

import AndroidKeys._
import AndroidHelpers._

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.Log
import com.android.ddmlib.RawImage

import java.io.{File, OutputStream, IOException}
import java.awt.image.{BufferedImage, RenderedImage}
import javax.imageio.ImageIO

object AndroidDdm {
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

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    screenshotDevice <<= (dbPath) map { p =>
      screenshot(false, false, p.absolutePath).getOrElse(error("could not get screenshot")).toFile("png", "device.png")
      file("device.png")
    },
    screenshotEmulator <<= (dbPath) map { p =>
      screenshot(true, false, p.absolutePath).getOrElse(error("could not get screenshot")).toFile("png", "emulator.png")
      file("emulator.png")
    }
  )) ++ Seq (
    screenshotDevice <<= (screenshotDevice in Android).identity,
    screenshotEmulator <<= (screenshotEmulator in Android).identity
  )
}
