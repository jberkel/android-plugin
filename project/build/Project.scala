import sbt._

class AndroidPlugin(info: ProjectInfo) extends PluginProject(info) 
	with posterous.Publish with sxr.Publish
{
	val publishTo = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
	Credentials(Path.fromFile(System.getProperty("user.home")) / ".ivy2" / ".credentials", log)
	
	val proguard = "net.sf.proguard" % "proguard" % "4.4"
}
