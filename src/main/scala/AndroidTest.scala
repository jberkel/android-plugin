import sbt._
import Keys._

import AndroidKeys._
import AndroidHelpers._
import complete.DefaultParsers._
import complete.Parser
import sbinary.DefaultProtocol.StringFormat
import Cache.seqFormat

object AndroidTest {

  val DefaultInstrumentationRunner = "android.test.InstrumentationTestRunner"

  def instrumentationTestAction(emulator: Boolean) = (dbPath, manifestPackage, instrumentationRunner, streams) map {
    (dbPath, manifestPackage, inst, s) =>
      val action = Seq("shell", "am", "instrument", "-w",
        manifestPackage + "/" + inst)
      adbTask(dbPath.absolutePath, emulator, s, action: _*)
  }

  def runSingleTest(emulator: Boolean) = (test: TaskKey[String]) => (test, dbPath, manifestPackage, streams) map {  (test, dbPath, manifestPackage, s) =>
      val action = Seq("shell", "am", "instrument", "-w", "-e", "class", test, manifestPackage+"/android.test.InstrumentationTestRunner")
      adbTask(dbPath.absolutePath, emulator, s, action:_*)
  }

  def testParser(s: State, tests:Seq[String]): Parser[String] =
    Space ~> tests.map(t => token(t))
                  .reduceLeftOption(_ | _)
                  .getOrElse(token(NotSpace))

  /** AndroidTestProject */
  lazy val androidSettings = settings ++
    inConfig(Android)( Seq(
      proguardInJars <<= (scalaInstance) map {
        (scalaInstance) =>
         Seq(scalaInstance.libraryJar)
      }
    )
  )

  lazy val settings: Seq[Setting[_]] =
    AndroidBase.settings ++
    AndroidInstall.settings ++
    inConfig(Android) (Seq (
      instrumentationRunner := DefaultInstrumentationRunner,
      testEmulator <<= instrumentationTestAction(true),
      testDevice   <<= instrumentationTestAction(false),
      testOnlyEmulator <<= InputTask(loadForParser(definedTestNames in Test)( (s, i) => testParser(s, i getOrElse Nil))) { test =>
        runSingleTest(true)(test)
      },
      testOnlyDevice   <<= InputTask(loadForParser(definedTestNames in Test)( (s, i) => testParser(s, i getOrElse Nil))) { test =>
        runSingleTest(false)(test)
      }
    )) ++ Seq (
      testEmulator <<= (testEmulator in Android),
      testDevice   <<= (testDevice in Android),
      testOnlyEmulator <<= (testOnlyEmulator in Android),
      testOnlyDevice   <<= (testOnlyDevice in Android)
    )
}
