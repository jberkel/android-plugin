import sbt._
import Keys._

import AndroidKeys._
import AndroidHelpers._

object AndroidTest {
  def instrumentationTestAction(emulator: Boolean) = (dbPath, manifestPackage, streams) map {
    (dbPath, manifestPackage, s) =>
      val action = Seq("shell", "am", "instrument", "-w",
                       manifestPackage+"/android.test.InstrumentationTestRunner")
      adbTask(dbPath.absolutePath, emulator, s, action:_*)
    }

  /** AndroidTestProject */
  lazy val androidSettings = settings

  lazy val settings: Seq[Setting[_]] =
    AndroidBase.settings ++
    AndroidInstall.settings ++
    inConfig(Android) (Seq (
      testEmulator <<= instrumentationTestAction(true),
      testDevice <<= instrumentationTestAction(false),
      skipApkLibDependencies := true
    )) ++ Seq (
      testEmulator <<= (testEmulator in Android),
      testDevice <<= (testDevice in Android)
    )
}
