import sbt._

import Keys._
import AndroidKeys._

object AndroidMarketPublish {

  private def prepareMarketTask = (packageAlignedPath, streams) map { (path, s) =>
    s.log.success("Ready for publication: \n" + path)
    path
  }

  private def zipAlignTask: Project.Initialize[Task[File]] =
    (zipAlignPath, packageApkPath, packageAlignedPath, streams) map { (zip, apkPath, pPath, s) =>
      val zipAlign = Seq(
          zip.absolutePath,
          "-v", "4",
          apkPath.absolutePath,
          pPath.absolutePath)
      s.log.debug("Aligning "+zipAlign.mkString(" "))
      s.log.debug(zipAlign !!)
      s.log.info("Aligned "+pPath)
      pPath
    }

  private def signReleaseTask: Project.Initialize[Task[File]] =
    (keystorePath, keyalias, packageApkPath, streams, cachePasswords) map { (ksPath, ka, pPath, s, cache) =>
      val jarsigner = Seq(
        "jarsigner",
        "-verbose",
        "-keystore", ksPath.absolutePath,
        "-storepass", PasswordManager.get(
              ksPath.absolutePath.replace("/","_"), ka, cache).getOrElse(sys.error("could not get password")),
        pPath.absolutePath,
        ka)
      s.log.debug("Signing "+jarsigner.mkString(" "))
      s.log.debug(jarsigner !!)
      s.log.info("Signed "+pPath)
      pPath
    }

  private def getPassword = SimpleReader.readLine("\nEnter keystore password: ").get

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq(
    // Configuring Settings
    keystorePath := Path.userHome / ".keystore",
    zipAlignPath <<= (toolsPath) { _ / "zipalign" },
    packageAlignedName <<= (artifact, version) ((a,v) =>
                                                String.format("%s-%s-market.apk", a.name, v)),
    packageAlignedPath <<= (target, packageAlignedName) { _ / _ },

    // Configuring Tasks
    cleanAligned <<= (packageAlignedPath) map (IO.delete(_)),

    prepareMarket <<= prepareMarketTask,
    prepareMarket <<= prepareMarket dependsOn zipAlign,

    zipAlign <<= zipAlignTask,
    zipAlign <<= zipAlign dependsOn (signRelease, cleanAligned),

    signRelease <<= signReleaseTask,
    signRelease <<= signRelease dependsOn packageRelease
  )) ++ Seq (
    cleanFiles <+= (packageAlignedPath in Android)
  )
}
