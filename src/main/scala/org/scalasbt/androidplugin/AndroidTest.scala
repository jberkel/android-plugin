package org.scalasbt.androidplugin

import sbt._
import Keys._

import AndroidKeys._
import AndroidHelpers._
import complete.DefaultParsers._
import complete.Parser
import sbinary.DefaultProtocol.StringFormat
import Cache.seqFormat
import com.android.ddmlib.testrunner.{InstrumentationResultParser,ITestRunListener}

object AndroidTest {

  val defaultTestRunner = "android.test.InstrumentationTestRunner"

  def detectTestRunnerTask = (manifestPath) map { (mp) =>
    val instrumentations = (manifest(mp.head) \ "instrumentation").map(_.attribute(
        "http://schemas.android.com/apk/res/android", "name"))
    instrumentations.headOption.flatMap(_.map(_.toString)).getOrElse(defaultTestRunner)
  }


  def instrumentationTestAction(emulator: Boolean) = (dbPath, manifestPackage, testRunner, streams) map {
    (dbPath, manifestPackage, testRunner, s) =>
      val action = Seq("shell", "am", "instrument", "-r", "-w", manifestPackage+"/"+testRunner)
      val (exit, out) = adbTaskWithOutput(dbPath.absolutePath, emulator, s, action:_*)
      if (exit == 0) parseTests(out, manifestPackage, s.log)
      else sys.error("am instrument returned error %d\n\n%s".format(exit, out))
      ()
    }

  def runSingleTest(emulator: Boolean) = (test: TaskKey[String]) => (test, dbPath, manifestPackage, testRunner, streams) map {
        (test, dbPath, manifestPackage, testRunner, s) =>
      val action = Seq("shell", "am", "instrument", "-r", "-w", "-e", "class", test, manifestPackage+"/"+
                       testRunner)
      val (exit, out) = adbTaskWithOutput(dbPath.absolutePath, emulator, s, action:_*)
      if (exit == 0) parseTests(out, manifestPackage, s.log)
      else sys.error("am instrument returned error %d\n\n%s".format(exit, out))
      ()
  }

  def parseTests(out: String, name: String, log: Logger) {
    val listener = new TestListener(log)
    val parser = new InstrumentationResultParser(name, listener)
    parser.processNewLines(out.split("\\n").map(_.trim))
    listener.errorMessage.map(sys.error(_)).orElse {
      log.success("All tests passed")
      None
    }
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
      testRunner   <<= detectTestRunnerTask,
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

  class TestListener(log: Logger) extends ITestRunListener {
    import com.android.ddmlib.testrunner.TestIdentifier
    import com.android.ddmlib.testrunner.ITestRunListener._
    type Metrics = java.util.Map[String, String]

    val failedTests = new collection.mutable.ListBuffer[TestIdentifier]
    var runFailed:Option[String] = None

    def testRunStarted(runName: String, testCount: Int) {
      log.info("testing %s (%d test%s)".format(runName, testCount, if (testCount == 1) "" else "s"))
    }

    def testRunStopped(elapsedTime: Long) {
      log.info("testRunStopped (%d seconds)".format(elapsedTime))
    }

    def testStarted(test: TestIdentifier) {
      log.debug("testStarted: "+test)
    }

    def testEnded(test: TestIdentifier, metrics: Metrics) {
      if (!failedTests.contains(test)) {
        val status = "%spassed%s: %s".format(scala.Console.GREEN, scala.Console.RESET, test)
        if (test.getTestName != "testAndroidTestCaseSetupProperly") {
          log.info(status)
        } else {
          log.debug(status)
        }
      }
      log.debug(metrics.toString)
    }

    def testFailed(status: TestFailure, test: TestIdentifier, trace: String) {
      log.error("failed: %s\n\n%s\n".format(test, trace))
      failedTests += test
    }

    def testRunEnded(elapsedTime: Long, metrics: Metrics) {
      log.info("testRunEnded (%d seconds)".format(elapsedTime))
      log.debug(metrics.toString)
    }

    def testRunFailed(message: String) {
      log.error("testRunFailed: "+message)
      runFailed = Some(message)
    }

    def errorMessage:Option[String] = {
      if (!failedTests.isEmpty)
        Some("Failed tests: "+failedTests.mkString(", "))
      else runFailed
    }
  }
}
