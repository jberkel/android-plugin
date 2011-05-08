import sbt._

abstract class AndroidProject(info: ProjectInfo) extends BaseAndroidProject(info) with Startable
