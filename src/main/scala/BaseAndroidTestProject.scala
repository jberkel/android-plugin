import sbt._

abstract class BaseAndroidTestProject(info: ProjectInfo) extends BaseAndroidProject(info) with Installable {

  lazy val testEmulator = instrumentationTestAction(true) describedAs("runs tests in emulator") dependsOn reinstallEmulator
  lazy val testDevice = instrumentationTestAction(false) describedAs("runs tests on device") dependsOn reinstallDevice

  def instrumentationTestAction(emulator:Boolean) = adbTask(emulator, "shell am instrument -w "+manifestPackage+"/android.test.InstrumentationTestRunner") describedAs("runs instrumentation tests")        
}
