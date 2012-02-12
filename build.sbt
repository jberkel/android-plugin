name := "sbt-android-plugin"

organization := "org.scala-tools.sbt"

version := "0.6.3-SNAPSHOT"

scalacOptions += "-deprecation"

publishMavenStyle := true

publishTo <<= (version) { version: String =>
    val nexus = "http://nexus.scala-tools.org/content/repositories/"
    if (version.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "snapshots/")
    else                                   Some("releases"  at nexus + "releases/")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

libraryDependencies ++= Seq(
  "com.google.android.tools" % "ddmlib" % "r10",
  "net.sf.proguard" % "proguard-base" % "4.6"
)

sbtPlugin := true
