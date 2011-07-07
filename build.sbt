organization := "org.scala-tools.sbt"

name := "sbt-android-plugin"

version := "0.5.2-SNAPSHOT" 

scalacOptions += "-deprecation"

publishTo := Some("Scala Tools Nexus" at 
                  "http://nexus.scala-tools.org/content/repositories/releases/")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

libraryDependencies ++= Seq(
  "com.google.android.tools" % "ddmlib" % "r10", 
  "net.sf.proguard" % "proguard" % "4.4"
)

sbtPlugin := true

// These settings should go in ~/.sbt/plugins/user.scala until issue #52 is resolved

posterousEmail := "blah@example.com"

posterousPassword := "this is not really my password"
