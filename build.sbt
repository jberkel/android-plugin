organization := "org.scala-tools.sbt"

name := "sbt-android-plugin"

version := "0.5.2-SNAPSHOT" 

scalacOptions += "-deprecation"

publishTo := Some("Scala Tools Nexus" at 
                  "http://nexus.scala-tools.org/content/repositories/releases/")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

libraryDependencies += "com.google.android.tools" % "ddmlib" % "r10" 

sbtPlugin := true

posterousEmail := "blah@example.com"

posterousPassword := "this is not really my password"
