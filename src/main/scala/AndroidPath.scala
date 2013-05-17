package sbtandroid

import sbt._

import Keys._
import AndroidKeys._
import AndroidHelpers._

object AndroidPath {

  lazy val settings: Seq[Setting[_]] = inConfig(Android) {
    AndroidDefaults.settings ++ Seq (
    osDxName <<= (dxName) (_ + osBatchSuffix),

    toolsPath <<= (sdkPath) (_ / "tools"),
    dbPath <<= (platformToolsPath, adbName) (_ / _),
    platformToolsPath <<= (sdkPath) (_ / "platform-tools"),
    buildToolsPath <<= (sdkPath, platformToolsPath, buildToolsVersion) { (sdkPath, platformToolsPath, buildToolsVersion) =>
      val btp = sdkPath / "build-tools"
      if (btp.exists()) (btp / buildToolsVersion) else platformToolsPath
    },
    aaptPath <<= (buildToolsPath, aaptName) (_ / _),
    idlPath <<= (buildToolsPath, aidlName) (_ / _),
    dxPath <<= (buildToolsPath, osDxName) (_ / _),

    sdkPath <<= (envs, baseDirectory) { (es, dir) =>
      determineAndroidSdkPath(es).getOrElse {
        val local = new File(dir, "local.properties")
        if (local.exists()) {
          (for (sdkDir <- (for (l <- IO.readLines(local);
               if (l.startsWith("sdk.dir")))
               yield l.substring(l.indexOf('=')+1)))
               yield new File(sdkDir)).headOption.getOrElse(
                sys.error("local.properties did not contain sdk.dir")
               )
        } else sys.error(
            "Android SDK not found. You might need to set %s".format(es.mkString(" or "))
          )
      }
    }
    )
  }
}
