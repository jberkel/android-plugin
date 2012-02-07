import sbt._

import Keys._
import AndroidKeys._

object PasswordManager extends PWManager {
  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    clearPasswords <<= (streams) map { (s) =>
      clear()
      s.log.success("cleared passwords")
    }
  ))

  def fetch(service: String, account: String): Option[String] = impl.fetch(service, account)
  def store(service: String, account: String, pw: String): Option[String] = impl.store(service, account, pw)
  def delete(service: String, account: String): Boolean = impl.delete(service, account)
  def clear() { impl.clear() }

  lazy val impl: PWManager = {
    System.getProperty("os.name") match {
      case "Mac OS X" => OSXPasswordManager
      case unknown    => FilePasswordManager
    }
  }
}

trait PWManager {
  def readPassword(service: String, account: String) =
      SimpleReader.readLine("\nEnter password for "+service+"/"+account+": ").get

  def get(service: String, account: String, cache: Boolean):Option[String] = {
      fetch(service, account).orElse {
        val pw = readPassword(service, account)
        if (cache) store(service, account, pw) else Some(pw)
      }
    }


  def fetch(service: String, account: String): Option[String]
  def store(service: String, account: String, pw: String): Option[String]
  def delete(service: String, account: String): Boolean
  def clear()
}

object OSXPasswordManager extends PWManager {
  val Label = "sbt-android-plugin"

  def fetch(service: String, account: String): Option[String] = {

    val buffer = new StringBuffer
    Seq("security",
      "find-generic-password",
      "-a", account,
      "-s", service, "-g").run(new ProcessIO(input => (),
      output => (),
      error => buffer.append(IO.readStream(error)))).exitValue() match {
        case 0 =>
          (for (line <- buffer.toString.split("\r\n");
                if (line.startsWith("password: ")))
          yield line.substring(line.indexOf('"') + 1, line.lastIndexOf('"'))).headOption
        case 44 =>
          // password not stored yet
          None
        case _ => None
      }
  }

  def store(service: String, account: String, pw: String): Option[String] = {
    Seq("security",
      "add-generic-password",
      "-a", account,
      "-s", service,
      "-l", Label,
      "-w", pw).run(false).exitValue() match {
      case 0 => Some(pw)
      case _ => None
    }
  }

  def delete(service: String, account: String): Boolean = {
    Seq("security",
      "delete-generic-password",
      "-a", account,
      "-s", service).run(false).exitValue() == 0
  }

  def clear() {
    if (Seq("security",
      "delete-generic-password",
      "-l", Label
      ).run(new ProcessIO(input => (), output => (), error => ()))
       .exitValue() == 0) clear()
  }
}

object EmptyPasswordManager extends PWManager {
  def fetch(service: String, account: String) = None
  def store(service: String, account: String, pw: String) = Some(pw)
  def delete(service: String, account: String) = false
  def clear() {}
}

object FilePasswordManager extends PWManager {
  val pwDir = new File(new File(
      System.getProperty("user.home"), ".sbt"), "sbt-android-plugin-passwords")

  def file(service: String) = {
      if (!pwDir.exists()) pwDir.mkdirs()
      new File(pwDir, service)
  }

  def fetch(service: String, account: String) = {
    val f = file(service)
    if (f.exists()) (for (line <- IO.readLines(f);
        if line.startsWith(account+"="))
        yield line.substring(line.indexOf('=')+1)).headOption
    else None
  }

  def store(service: String, account: String, pw: String) = {
    val f = file(service)
    val buffer = new StringBuffer
    var replaced = false
    def appendPw() = buffer.append(account).append("=").append(pw).append("\n")
    if (f.exists()) {
      for (line <- IO.readLines(f))
        if (line.startsWith(account+"=")) {
          appendPw()
          replaced = true
        } else buffer.append(line).append("\n")
      if (!replaced) appendPw()
    } else appendPw()
    IO.write(f, buffer.toString.getBytes)
    Some(pw)
  }

  def clear() {
    if (pwDir.exists()) {
      for (f <- pwDir.listFiles()) f.delete()
      pwDir.delete()
    }
  }

  def delete(service: String, account: String) = {
    val f = file(service)
    val buffer = new StringBuffer
    var found = false
    if (f.exists()) {
      for (line <- IO.readLines(f)) {
        if (!line.startsWith(account+"=")) {
          buffer.append(line).append("\n")
        } else found = true
      }
      IO.write(f, buffer.toString.getBytes)
      found
    } else false
  }
}
