import sbt._

import Keys._
import AndroidKeys._

object AndroidMarketPublish {

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

  lazy val settings = inConfig(Android) (Seq(
    // Configuring Settings
    keystorePath := Path.userHome / ".keystore",
    zipAlignPath <<= (toolsPath) { _ / "zipalign" },
    packageAlignedName <<= (artifact) { _.name + "-market" + ".apk"},
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
    cleanFiles <+= (packageAlignedPath in Android).identity,
    prepareMarket <<= (prepareMarket in Android).identity
  )
}
