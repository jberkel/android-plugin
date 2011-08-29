import sbt._

import Keys._
import AndroidKeys._

import scala.xml._

object AndroidManifestGenerator {
  private def generateManifestTask =
    (manifestPath, manifestTemplatePath, versionCode, version) map {
    (manifestPath, manifestTemplatePath, versionCode, version) =>

      val namespacePrefix = "http://schemas.android.com/apk/res/android"
      val manifest = XML.loadFile(manifestTemplatePath)
      if (manifest.attribute(namespacePrefix,"versionCode").isDefined)
        error("android:versionCode should not be defined in template")
      if (manifest.attribute(namespacePrefix, "versionName").isDefined)
        error("android:versionName should not be defined in template")
      val applications = manifest \ "application"
      val wasDebuggable =
        applications.exists(_.attribute(namespacePrefix, "debuggable").isDefined)

      val verName =
        new PrefixedAttribute("android", "versionName", version, Null)
      val verCode =
        new PrefixedAttribute("android", "versionCode", versionCode.toString, Null)

      val newManifest = manifest % verName % verCode

      XML.save(manifestTemplatePath.absolutePath, newManifest)

      manifestPath
    }

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq(
    manifestTemplateName := "AndroidManifest.xml",
    manifestTemplatePath <<= (sourceDirectory in Compile, manifestTemplateName)(_/_),

    manifestPath <<= (baseDirectory, manifestName) (_ / "src_managed" / "main" / _),
    cleanManifest <<= (manifestPath) map (IO.delete(_)),

    generateManifest <<= generateManifestTask,
    generateManifest <<= generateManifest dependsOn makeManagedJavaPath,
    aaptGenerate <<= aaptGenerate dependsOn generateManifest
  )) ++ Seq (
    cleanFiles <+= (manifestPath in Android).identity
  )
}
