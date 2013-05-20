package org.scalasbt.androidplugin

import sbt._
import Keys._
import AndroidPlugin._

/** Some sensible defaults for building java projects with the plugin */
object PlainJavaProject {
  lazy val settings: Seq[Setting[_]] = (Seq(
    useProguard := false,
    autoScalaLibrary in GlobalScope := false,
    manifestPath <<= (baseDirectory, manifestName) map((s,m) => Seq(s / m)) map (x=>x),
    proguardOptimizations := Seq.empty,
    mainResPath <<= (baseDirectory, resDirectoryName) (_ / _) map (x=>x),
    mainAssetsPath <<= (baseDirectory, assetsDirectoryName) (_ / _),
    javaSource <<= (baseDirectory) (_ / "src")
    )
  )
}
