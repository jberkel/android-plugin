import sbt._

import Keys._
import AndroidKeys._

object MarketPublish extends Plugin {

  val keyalias = SettingKey[String]("key-alias")
  val keystorePath = SettingKey[File]("key-store-path")
  val zipAlignPath = SettingKey[File]("zip-align-path", "Path to zipalign")
  val packageAlignedName = SettingKey[String]("package-aligned-name")
  val packageAlignedPath = SettingKey[File]("package-aligned-path")

  val prepareMarket = TaskKey[Unit]("prepare-market", "Prepare asset for Market publication.")
  val zipAlign = TaskKey[Unit]("zip-align", "Run zipalign on signed jar.")
  val signRelease = TaskKey[Unit]("sign-release", "Sign with key alias using key-alias and keystore path.")

  val marketSettings = inConfig(AndroidConfig) (Seq(
    keystorePath := Path.userHome / ".keystore",
    zipAlignPath <<= (toolsPath) { _ / "zipalign" },
    packageAlignedName <<= (artifact) { _.name + "-market" + ".apk"},
    packageAlignedPath <<= (target, packageAlignedName) { _ / _ }
  ))
}
