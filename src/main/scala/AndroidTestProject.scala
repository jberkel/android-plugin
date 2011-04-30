import sbt._

abstract class AndroidTestProject(info: ProjectInfo) extends AndroidProject(info) {
  override def proguardInJars = Path.emptyPathFinder

  // Normally it makes no sense to start a test application, so make these actions no-ops.
  override def startEmulatorAction = task { None }
  override def startDeviceAction = task { None }

  lazy val testEmulator = instrumentationTestAction(true) describedAs("runs tests in emulator") dependsOn reinstallEmulator
  lazy val testDevice = instrumentationTestAction(false) describedAs("runs tests on device") dependsOn reinstallDevice

  def instrumentationTestAction(emulator:Boolean) = adbTask(emulator, "shell am instrument -w "+manifestPackage+"/android.test.InstrumentationTestRunner") describedAs("runs instrumentation tests")        
}
