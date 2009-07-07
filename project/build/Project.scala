import sbt._

class AndroidPlugin(info: ProjectInfo) extends PluginProject(info)
{
	val publishTo = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
	Credentials(Path.fromFile(System.getProperty("user.home")) / ".ivy2" / ".credentials", log)
	
	val proguard = "net.sf.proguard" % "proguard" % "4.3"
	val ant = "org.apache.ant" % "ant" % "1.7.1" intransitive()
	val regexp = "org.apache.ant" % "ant-apache-regexp" % "1.7.1" intransitive()
	val antLauncher = "org.apache.ant" % "ant-launcher" % "1.7.1"
	val antelese = "com.netgents" % "antelese" % "0.1" from "http://cloud.github.com/downloads/weihsiu/antelese/antelese-0.1.jar"
}
