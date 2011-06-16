import sbt._

abstract class AndroidTestProject(info: ProjectInfo) extends BaseAndroidTestProject(info) {

  override def proguardInJars = Path.emptyPathFinder
}
