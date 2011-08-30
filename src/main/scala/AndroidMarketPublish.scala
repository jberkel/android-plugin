import sbt._

import Keys._
import AndroidKeys._

object AndroidMarketPublish {

  private def prepareMarketTask = (packageAlignedPath, streams) map { (path, s) =>
    s.log.success("Ready for publication: \n" + path)
  }

  private def zipAlignTask: Project.Initialize[Task[File]] =
    (zipAlignPath, packageApkPath, packageAlignedPath, streams) map { (zip, apkPath, pPath, s) =>
      s.log.debug(Process(<x> {zip} -v 4 {apkPath} {pPath} </x>).!!)
      s.log.info("Aligned "+pPath)
      pPath
    }

  private def signReleaseTask: Project.Initialize[Task[File]] =
    (keystorePath, keyalias, packageApkPath, streams) map { (ksPath, ka, pPath,s ) =>
      s.log.debug(Process(
        <x> jarsigner -verbose -keystore {ksPath} -storepass {getPassword} {pPath} {ka} </x>).!!)
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
    cleanFiles <+= (packageAlignedPath in Android).identity
  )
}
