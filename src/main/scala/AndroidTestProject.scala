import sbt._

class AndroidTestProject(info: ProjectInfo) extends AndroidProject(info) {
  override def proguardInJars = Path.emptyPathFinder
  
  lazy val runTest = runTestAction
  def runTestAction = adbTask(true, "shell am instrument -w "+manifestPackage+"/android.test.InstrumentationTestRunner") describedAs("runs instrumentation tests")        
}
