package org.scalasbt.androidplugin

import sbt._

import Keys._
import AndroidPlugin._

import scala.xml._

object AndroidManifestGenerator {
  private def generateManifestTask =
    (resourceManaged in Compile, manifestTemplatePath, versionCode, version, streams) map {
    (basedir, manifestTemplatePath, versionCode, version, streams) =>

      val namespacePrefix = "http://schemas.android.com/apk/res/android"
      val manifest = XML.loadFile(manifestTemplatePath)
      if (manifest.attribute(namespacePrefix,"versionCode").isDefined)
        sys.error("android:versionCode should not be defined in template")
      if (manifest.attribute(namespacePrefix, "versionName").isDefined)
        sys.error("android:versionName should not be defined in template")
      val applications = manifest \ "application"

      val verName =
        new PrefixedAttribute("android", "versionName", version, Null)
      val verCode =
        new PrefixedAttribute("android", "versionCode", versionCode.toString, Null)

      val newManifest = manifest % verName % verCode

      val out = basedir / "AndroidManifest.xml"
      basedir.mkdirs()

      XML.save(out.absolutePath, newManifest)
      streams.log.debug("Generated "+out)
      Seq(out)
    }

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq(
    manifestTemplateName := "AndroidManifest.xml",
    manifestTemplatePath <<= (sourceDirectory in Compile, manifestTemplateName)(_/_),

    manifestPath <<= generateManifestTask,
    generateManifest <<= generateManifestTask
  ))
}
