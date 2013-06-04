package sbtandroid

import sbt._

import Keys._
import AndroidKeys._

object AndroidMarketPublish {

  def zipAlignTask: Project.Initialize[Task[File]] =
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

   def signReleaseTask: Project.Initialize[Task[File]] =
    (keystorePath, keyalias, packageApkPath, streams, cachePasswords) map { (ksPath, ka, pPath, s, cache) =>
      val jarsigner = Seq(
        "jarsigner",
        "-verbose",
        "-keystore", ksPath.absolutePath,
        "-storepass", PasswordManager.get(
              "keystore", ka, cache).getOrElse(sys.error("could not get password")),
        pPath.absolutePath,
        ka)
      s.log.debug("Signing "+jarsigner.mkString(" "))
      val out = new StringBuffer
      val exit = jarsigner.run(new ProcessIO(input => (),
                            output => out.append(IO.readStream(output)),
                            error  => out.append(IO.readStream(error)),
                            inheritedInput => false)
                        ).exitValue()
      if (exit != 0) sys.error("Error signing: "+out)
      s.log.debug(out.toString)
      s.log.info("Signed "+pPath)
      pPath
    }

  private def prepareMarketTask = (packageAlignedPath, streams) map { (path, s) =>
    s.log.success("Ready for publication: \n" + path)
    path
  }

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq(
    // Configuring Settings
    keystorePath := Path.userHome / ".keystore",
    zipAlignPath <<= (toolsPath) { _ / "zipalign" },
    packageAlignedName <<= (artifact, versionName) map ((a,v) =>
                                                String.format("%s-%s-market.apk", a.name, v)),
    packageAlignedPath <<= (target, packageAlignedName) map ( _ / _ ),

    // Configuring Tasks
    cleanAligned <<= (packageAlignedPath) map (IO.delete(_)),

    prepareMarket <<= prepareMarketTask,
    prepareMarket <<= prepareMarket dependsOn zipAlign,

    zipAlign <<= zipAlignTask,
    zipAlign <<= zipAlign dependsOn (signRelease, cleanAligned),

    signRelease <<= signReleaseTask,
    signRelease <<= signRelease dependsOn packageRelease
  ))
}
