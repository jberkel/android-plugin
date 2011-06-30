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

  val cleanAligned = TaskKey[Unit]("clean-aligned", "Remove zipaligned jar")

  private def prepareMarketTask = (packageAlignedPath, streams) map { (path, s) =>
    s.log.success("Ready for publication: \n" + path)
  }

  private def zipAlignTask: Project.Initialize[Task[Unit]] = 
    (zipAlignPath, packageApkPath, packageAlignedPath) map { (zip, apkPath, pPath) =>
      Process(<x>
        {zip} -v 4 {apkPath} {pPath}
      </x>) !
    }

  private def signReleaseTask: Project.Initialize[Task[Unit]] =
    (keystorePath, keyalias, packageApkPath) map { (ksPath, ka, pPath) =>
      Process(<x>
        jarsigner -verbose -keystore {ksPath} -storepass {getPassword} {pPath} {ka}
      </x>) !
    }

  private def getPassword = SimpleReader.readLine("\nEnter keystore password: ").get

  val marketSettings = inConfig(AndroidConfig) (Seq(
    // Configuring Settings
    keystorePath := Path.userHome / ".keystore",
    zipAlignPath <<= (toolsPath) { _ / "zipalign" },
    packageAlignedName <<= (artifact) { _.name + "-market" + ".apk"},
    packageAlignedPath <<= (target, packageAlignedName) { _ / _ },

    // Configuring Tasks
    cleanAligned <<= (packageAlignedPath) map (IO.delete(_)),
    cleanFiles <+= packageAlignedPath.identity,

    prepareMarket <<= prepareMarketTask,
    prepareMarket <<= prepareMarket dependsOn zipAlign,

    zipAlign <<= zipAlignTask,
    zipAlign <<= zipAlign dependsOn (signRelease, cleanAligned),

    signRelease <<= signReleaseTask,
    signRelease <<= signRelease dependsOn packageRelease
  ))
}
