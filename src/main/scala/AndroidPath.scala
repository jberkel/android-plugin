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
    aaptPath <<= (platformToolsPath, aaptName) (_ / _),
    idlPath <<= (platformToolsPath, aidlName) (_ / _),
    dxPath <<= (platformToolsPath, osDxName) (_ / _),

    sdkPath <<= (envs) { es =>
      determineAndroidSdkPath(es).getOrElse(sys.error(
        "Android SDK not found. You might need to set %s".format(es.mkString(" or "))
      ))
    }
  ) }
}
