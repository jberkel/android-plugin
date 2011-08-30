import sbt._
import Keys._
import complete.DefaultParsers._

import AndroidKeys._
import AndroidHelpers._

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.IDevice
import com.android.ddmlib.Log
import com.android.ddmlib.RawImage
import com.android.ddmlib.ClientData
import com.android.ddmlib.Client
import com.android.ddmlib.ThreadInfo
import com.android.ddmlib.ClientData.IHprofDumpHandler

import java.io.{File, OutputStream, FileOutputStream, IOException}
import java.awt.image.{BufferedImage, RenderedImage}
import javax.imageio.ImageIO

object AndroidDdm {
  var bridge: Option[AndroidDebugBridge] = None
  val infos = scala.collection.mutable.Map.empty[String, ThreadInfo]

  val THREAD_STATUS = Array[String](
    "zombie", "running", "timed-wait", "monitor",
    "wait", "init", "start", "native", "vmwait",
    "suspended")

  val clientListener = new IClientChangeListener() {
    override def clientChanged(client: Client, mask: Int) {
      mask match {
        case Client.CHANGE_THREAD_DATA =>
        case Client.CHANGE_THREAD_STACKTRACE =>
          if (client.getClientData.getThreads != null) {
            for (tinfo <- client.getClientData.getThreads; if
                 tinfo.getStackTrace != null) {
                 infos.put(tinfo.getThreadName(), tinfo)
            }
          }
        case _ =>
      }
    }
  }

  def createBridge(path: String, clientSupport: Boolean) = {
    bridge.getOrElse {
      AndroidDebugBridge.addClientChangeListener(clientListener)
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

  def withDevice[F](emulator: Boolean, path: String)
                   (action: IDevice => F):Option[F] = {
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

  def withClient[F](emulator: Boolean, path: String, clientPkg: String)
                   (action: Client => F):Option[F] = {
    withDevice(emulator, path) { device =>
      var client = device.getClient(clientPkg)
      var count = 0
      while (client == null && count < 20) {
        client = device.getClient(clientPkg)
        Thread.sleep(100)
        count += 1
      }
      if (client != null) {
        action(client)
      } else
        error("could not get client "+clientPkg+", is it running?")
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
      Screenshot(image)
    }
  }

  def dumpHprof(app: String, path: String, emulator: Boolean)
               (success: (Client, Array[Byte]) => Unit)
               (failure:  (Client, String) => Unit) = {
    withClient(emulator, path, app) { client =>
        ClientData.setHprofDumpHandler(new IHprofDumpHandler() {
          override def onSuccess(path: String, client: Client) = error("not supported")
          override def onSuccess(data: Array[Byte], client: Client) = success(client, data)
          override def onEndFailure(client: Client, message:String) = failure(client, message)
        })
        client.dumpHprof()
    }
  }

  def fetchThreads(app: String, path: String, emulator: Boolean):Option[Array[ThreadInfo]] = {
    withClient(emulator, path, app) { client =>
      client.setThreadUpdateEnabled(true)
      client.requestThreadUpdate
      val threads = client.getClientData.getThreads
      if (threads != null)
        for (t <- threads) {
          client.requestThreadStackTrace(t.getThreadId)
        }
      threads
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

  case class Screenshot(val r: RenderedImage) {
    def toFile(format: String, f: File, str:TaskStreams):File   = { ImageIO.write(r, format, f); f }
    def toFile(format: String, s: String, str:TaskStreams):File = {
      val tstamp = System.currentTimeMillis().toString()
      val file = new File(String.format("%s-%s.%s", s, tstamp, format))
      str.log.info("screenshot written to "+file.getName)
      toFile(format, file, str)
    }
    def toOutputStream(format: String, o: OutputStream) = ImageIO.write(r, format, o)
  }

  def printStackTask = (parsed: TaskKey[String]) =>
    (parsed, streams) map { (p, s) =>
      val info = infos.get(p)
                       .getOrElse(error("stack trace not found"))

      s.log.info(p+" ("+status(info)+")")
      s.log.info(info.getStackTrace
                     .map(_.toString).mkString("\n"))
      ()
    }

  def status(ti: ThreadInfo) = THREAD_STATUS(ti.getStatus)

  def threadList(app: String, path: String, emu: Boolean) = (s: State) => {
    fetchThreads(app, path, emu)
    Space ~> infos.map({ case (k, v) => token(k) })
                   .reduceLeftOption(_ | _)
                   .getOrElse(token("none"))
  }

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    screenshotDevice <<= (dbPath, streams) map { (p,s) =>
      screenshot(false, false, p.absolutePath).getOrElse(error("could not get screenshot")).toFile("png", "device", s)
    },
    screenshotEmulator <<= (dbPath, streams) map { (p,s) =>
      screenshot(true, false, p.absolutePath).getOrElse(error("could not get screenshot")).toFile("png", "emulator", s)
    },
    hprofEmulator <<= (manifestPackage, dbPath) map { (m, p) =>
      dumpHprof(m, p.absolutePath, true)(writeHprof) { (client, message) => error(message) }
    },
    hprofDevice <<= (manifestPackage, dbPath) map { (m, p) =>
      dumpHprof(m, p.absolutePath, false)(writeHprof) { (client, message) => error(message) }
    },
    threadsEmulator <<= InputTask(
        (manifestPackage, dbPath) apply { (m,p) => threadList(m, p.absolutePath, true) })
        (printStackTask)
    ,
    threadsDevice <<= InputTask(
        (manifestPackage, dbPath) apply { (m,p) => threadList(m, p.absolutePath, false) })
        (printStackTask)
    ,
    stopBridge <<= (streams) map { (s) =>
      terminateBridge
      s.log.info("terminated debug bridge. older versions of the SDK might not be "+
                 "able to call init() again.")
    }
  ))
}
