

import sbt._
import Process._
import scala.xml._

object AndroidManifestGenerator {
  val DefaultAndroidManifestTemplateName = "AndroidManifest.xml"
}

/** Generates AndroidManifest.xml from AndroidManifestIn.xml, by applying various sbt properties.

This plug-in currently generates the manifest versionCode and versionName attributes.  It would be
possible to also generate correct settings for debug for market vs. debug builds but that is TODO.
*/
trait AndroidManifestGenerator extends AndroidProject {
  import AndroidManifestGenerator._

  /// The android version code - stored as a property, starts at 1 and increments whenever the user
  /// calls incrementVersion
  lazy val versionCode = propertyOptional[Int](1)

  def androidManifestTemplateName = DefaultAndroidManifestTemplateName 
  def androidManifestTemplatePath = mainSourcePath / androidManifestTemplateName

  override def androidManifestPath = "src_managed" / "main" / androidManifestName

  override def incrementVersionNumber() {
    super.incrementVersionNumber()

    versionCode() = versionCode.value + 1
    log.info("Android version code incremented to " + versionCode.value + ".  Manifest will be regenerated.")

    discardAndroidManifest()
  }

  /// Force regeneration of the manfest
  def discardAndroidManifest() {
    FileUtilities.clean(androidManifestPath, log)
  }

  /// a customized manifest file is needed for the following actions  
  override def aaptGenerateAction = super.aaptGenerateAction dependsOn(generateAndroidManifest)

  def generateAndroidManifestAction = fileTask(androidManifestPath from androidManifestTemplatePath) {

    val namespacePrefix = "http://schemas.android.com/apk/res/android"
    val manifest = XML.loadFile(androidManifestTemplatePath.asFile)
    if (manifest.attribute(namespacePrefix,"versionCode").isDefined) 
      error("android:versionCode should not be defined in template")
    if (manifest.attribute(namespacePrefix, "versionName").isDefined) 
      error("android:versionName should not be defined in template")
    val applications = manifest \ "application"
    val wasDebuggable = applications.exists(_.attribute(namespacePrefix, "debuggable").isDefined)

    val verName = new PrefixedAttribute("android", "versionName", version.toString, Null)
    val verCode = new PrefixedAttribute("android", "versionCode", versionCode.value.toString, Null)

    val newManifest = manifest % verName % verCode

    // Write the modified manifest
    XML.save(androidManifestPath.absolutePath, newManifest)

    None
  } describedAs("Generates a customized AndroidManifest.xml with current build number and debug settings.")

  lazy val generateAndroidManifest = generateAndroidManifestAction
}