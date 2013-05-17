package org.scalasbt.androidplugin

import sbt._

import Keys._
import AndroidPlugin._

import scala.xml._
import scala.xml.transform._

object AndroidManifestGenerator {

  /**
   * Rewrite rule to make an Android manifest consistent with the project's
   * version settings.
   */
  case class VersionRule(version: String, versionCode: Int) extends RewriteRule {
    val namespacePrefix = "http://schemas.android.com/apk/res/android"

    override def transform(n: scala.xml.Node): Seq[scala.xml.Node] = n match {

      case manifest: Elem if(manifest.label == "manifest") => {

        // Create or replace versionName and versionCode attributes to conform to
        // the project's settings.
        val verName =
          new PrefixedAttribute("android", "versionName", version, Null)
        val verCode =
          new PrefixedAttribute("android", "versionCode", versionCode.toString, Null)

        // Update the element
        manifest % verName % verCode
      }

      case other => other
    }
  }

  /**
   * This task transparently takes the AndroidManifest.xml file and applies a
   * series of transformations based on the project's settings.
   */
  private def generateManifestTask =
    (resourceManaged in Compile, manifestTemplatePath, manifestRewriteRules, streams) map {
    (basedir, manifestTemplatePath, rules, streams) =>

      // Load the AndroidManifest.xml file as a template
      val manifest = XML.loadFile(manifestTemplatePath)

      // Apply transformation rules
      val newManifest = new RuleTransformer(rules: _*)(manifest)

      // Create the output file and directories
      val out = basedir / "AndroidManifest.xml"
      basedir.mkdirs()

      // Save the final AndroidManifest.xml file
      XML.save(out.absolutePath, newManifest)
      streams.log.info("Generated "+out)
      Seq(out)
    }

  /**
   * Default settings that will override the default static AndroidManifest.xml
   * behavior.
   */
  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq(
    manifestRewriteRules <+= (version, versionCode) map (VersionRule(_, _)),

    manifestTemplateName := "AndroidManifest.xml",
    manifestTemplatePath <<= (sourceDirectory in Compile, manifestTemplateName)(_/_),

    manifestPath <<= generateManifestTask,
    generateManifest <<= generateManifestTask
  ))
}
