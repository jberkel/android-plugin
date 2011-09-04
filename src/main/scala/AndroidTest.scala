import sbt._
import Keys._

import AndroidKeys._
import AndroidHelpers._

object AndroidTest {
  def instrumentationTestAction(emulator: Boolean) = (dbPath, manifestPackage) map {
    (dbPath, manifestPackage) =>
      val action = "shell am instrument -w "+ manifestPackage +
                   "/android.test.InstrumentationTestRunner"
      adbTask(dbPath.absolutePath, emulator, action)
    }

  /** AndroidTestProject */
  lazy val androidSettings = settings ++ Seq (
    proguardInJars in Android := Nil
  )

  lazy val settings: Seq[Setting[_]] =
    AndroidBase.settings ++
    AndroidInstall.settings ++
    inConfig(Android) (Seq (
      testEmulator <<= instrumentationTestAction(true),
      testDevice <<= instrumentationTestAction(false)
    )) ++ Seq (
      testEmulator <<= (testEmulator in Android),
      testDevice <<= (testDevice in Android)
    )
}
