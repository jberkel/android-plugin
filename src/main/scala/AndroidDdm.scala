import sbt._
import Keys._

import AndroidKeys._
import AndroidHelpers._

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.Log
import com.android.ddmlib.RawImage
import com.android.ddmlib.ClientData
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData.IHprofDumpHandler

import java.io.{File, OutputStream, FileOutputStream, IOException}
import java.awt.image.{BufferedImage, RenderedImage}
import javax.imageio.ImageIO

object AndroidDdm {
  var bridge: Option[AndroidDebugBridge] = None

  def createBridge(path: String, clientSupport: Boolean) = {
    bridge.getOrElse {
      AndroidDebugBridge.init(clientSupport)
      java.lang.Runtime.getRuntime().addShutdownHook(new Thread() {
        override def run() { terminateBridge }
      })
      bridge = Some(AndroidDebugBridge.createBridge(path, false))
      bridge.get
    }
  }

  def terminateBridge {
    AndroidDebugBridge.terminate
    bridge = None
  }

  def withDevice[F](emulator: Boolean, path: String)(action: IDevice => F):Option[F] = {
    var count = 0
    val bridged = createBridge(path, true)

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

  // ported from http://dustingram.com/wiki/Device_Screenshots_with_the_Android_Debug_Bridge
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

  def dumpHprof(app: String, path: String, emulator: Boolean)
               (success: (Client, Array[Byte]) => Unit)
               (failure:  (Client, String) => Unit) = {
    withDevice(emulator, path) { device =>
      var client = device.getClient(app)
      var count = 0
      while (client == null && count < 20) {
        client = device.getClient(app)
        Thread.sleep(100)
        count += 1
      }
      if (client != null) {
        ClientData.setHprofDumpHandler(new IHprofDumpHandler() {
          override def onSuccess(path: String, client: Client) = error("not supported")
          override def onSuccess(data: Array[Byte], client: Client) = success(client, data)
          override def onEndFailure(client: Client, message:String) = failure(client, message)
        })
        client.dumpHprof()
      } else {
        error("could not get client "+app+", is it running?")
      }
    }
  }

  private def writeHprof(client: Client, data: Array[Byte]) = {
    val pkg = client.getClientData.getClientDescription
    val pid = client.getClientData.getPid
    val tmp = new File(pkg+".tmp")
    val fos = new FileOutputStream(tmp)
    fos.write(data)
    fos.close()
    val hprof = "%s-%d.hprof".format(pkg, pid)
    String.format("hprof-conv %s %s", tmp.getName, hprof).!
    tmp.delete()
    System.err.println("heap dump written to "+hprof)
    file(hprof)
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
    },
    hprofEmulator <<= (manifestPackage, dbPath) map { (m, p) =>
      dumpHprof(m, p.absolutePath, true)(writeHprof) { (client, message) => error(message) }
    },
    hprofDevice <<= (manifestPackage, dbPath) map { (m, p) =>
      dumpHprof(m, p.absolutePath, false)(writeHprof) { (client, message) => error(message) }
    },
    stopBridge <<= (streams) map { (s) =>
      terminateBridge
      s.log.info("terminated debug bridge. older versions of the SDK might not be "+
                 "able to call init() again.")
    }
  )) ++ Seq (
    screenshotDevice <<= (screenshotDevice in Android).identity,
    screenshotEmulator <<= (screenshotEmulator in Android).identity,
    hprofEmulator <<= (hprofEmulator in Android).identity,
    hprofDevice <<= (hprofDevice in Android).identity
  )
}
