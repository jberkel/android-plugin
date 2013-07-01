package sbtandroid

import sbt._
import Keys._
import AndroidPlugin._

/** Some sensible defaults for building java projects with the plugin */
object AndroidJavaLayout {
  lazy val settings: Seq[Setting[_]] = (Seq(
    autoScalaLibrary in GlobalScope := false,
    useProguard in Compile := false,
    useTypedResources in Compile := false,
    manifestPath in Compile <<= (baseDirectory, manifestName) map((s,m) => Seq(s / m)) map (x=>x),
    mainResPath in Compile <<= (baseDirectory, resDirectoryName) (_ / _) map (x=>x),
    mainAssetsPath in Compile <<= (baseDirectory, assetsDirectoryName) (_ / _),
    javaSource in Compile <<= (baseDirectory) (_ / "src"),
    unmanagedBase <<= baseDirectory (_ / "libs")
    )
  )
}
