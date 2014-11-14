name := "sbt-android"

organization := "org.scala-sbt"

scalaVersion := "2.10.2"

version := "0.7.1-SNAPSHOT"

//scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation", "-Xcheckinit", "-Xfatal-warnings")

scalacOptions ++= Seq("-unchecked", "-Xcheckinit")

publishMavenStyle := false

publishTo <<= (version) { version: String =>
    val scalasbt = "http://scalasbt.artifactoryonline.com/scalasbt/"
    val (name, url) = if (version.contains("-"))
                        ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
                      else
                        ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
    Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

libraryDependencies ++= Seq(
  "com.google.android.tools" % "ddmlib" % "r10",
  "net.sf.proguard" % "proguard-base" % "4.8"
)

sbtPlugin := true

commands += Status.stampVersion
