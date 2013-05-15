package org.scalasbt.androidplugin

import sbt._

import Keys._
import AndroidPlugin._

import scala.xml._

object AndroidAutoManifest {

  /**
   * This task transparently takes the AndroidManifest.xml file and applies a
   * series of transformations based on the project's settings.
   */
  private def generateManifestTask =
    (resourceManaged in Compile, manifestTemplatePath, versionCode, version, streams) map {
    (basedir, manifestTemplatePath, versionCode, version, streams) =>

      // Load the AndroidManifest.xml file as a template
      val namespacePrefix = "http://schemas.android.com/apk/res/android"
      val manifest = XML.loadFile(manifestTemplatePath)

      // Display warnings to the user if android:versionCode and
      // android:versionName are defined in both the project and the
      // AndroidManifest.xml template.
      for (v <- manifest.attribute(namespacePrefix,"versionCode"))
        if (v.toString != versionCode.toString)
          streams.log.warn("Overriding android:versionCode in final AndroidManifest.xml (was %s, set to %d)".format(v, versionCode))
      for (v <- manifest.attribute(namespacePrefix,"versionName"))
        if (v.toString != version)
          streams.log.warn("Overriding android:versionName in final AndroidManifest.xml (was %s, set to %s)".format(v, version))

      // Not sure what this is doing here
      val applications = manifest \ "application"

      // Create or replace versionName and versionCode attributes to conform to
      // the project's settings.
      val verName =
        new PrefixedAttribute("android", "versionName", version, Null)
      val verCode =
        new PrefixedAttribute("android", "versionCode", versionCode.toString, Null)

      val newManifest = manifest % verName % verCode

      // Create the output file and directories
      val out = basedir / "AndroidManifest.xml"
      basedir.mkdirs()

      // Save the final AndroidManifest.xml file
      XML.save(out.absolutePath, newManifest)
      streams.log.debug("Generated "+out)
      Seq(out)
    }

  /**
   * Default settings that will override the default static AndroidManifest.xml
   * behavior.
   */
  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq(
    manifestTemplateName := "AndroidManifest.xml",
    manifestTemplatePath <<= (sourceDirectory in Compile, manifestTemplateName)(_/_),

    manifestPath <<= generateManifestTask,
    generateManifest <<= generateManifestTask
  ))
}
