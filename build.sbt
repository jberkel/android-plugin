name := "sbt-android-plugin"

organization := "org.scala-tools.sbt"

version := "0.6.1-SNAPSHOT"

scalacOptions += "-deprecation"

publishMavenStyle := true

publishTo := Some("Scala Tools Nexus" at
                  "http://nexus.scala-tools.org/content/repositories/releases/")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

libraryDependencies ++= Seq(
  "com.google.android.tools" % "ddmlib" % "r10",
  "net.sf.proguard" % "proguard-base" % "4.6"
)

sbtPlugin := true
