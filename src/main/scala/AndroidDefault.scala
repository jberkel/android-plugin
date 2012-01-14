import sbt._

import Keys._
import AndroidKeys._

object AndroidDefaults {
  val DefaultAaaptName = "aapt"
  val DefaultAadbName = "adb"
  val DefaultAaidlName = "aidl"
  val DefaultDxName = "dx"
  val DefaultAndroidManifestName = "AndroidManifest.xml"
  val DefaultAndroidJarName = "android.jar"
  val DefaultAssetsDirectoryName = "assets"
  val DefaultResDirectoryName = "res"
  val DefaultClassesMinJarName = "classes.min.jar"
  val DefaultClassesDexName = "classes.dex"
  val DefaultResourcesApkName = "resources.apk"
  val DefaultDxOpts = ("-JXmx512m", None)
  val DefaultManifestSchema = "http://schemas.android.com/apk/res/android"
  val DefaultEnvs = List("ANDROID_SDK_HOME", "ANDROID_SDK_ROOT", "ANDROID_HOME")

  lazy val settings: Seq[Setting[_]] = Seq (
    aaptName := DefaultAaaptName,
    adbName := DefaultAadbName,
    aidlName := DefaultAaidlName,
    dxName := DefaultDxName,
    manifestName := DefaultAndroidManifestName, 
    jarName := DefaultAndroidJarName, 
    assetsDirectoryName := DefaultAssetsDirectoryName,
    resDirectoryName := DefaultResDirectoryName,
    classesMinJarName := DefaultClassesMinJarName,
    classesDexName := DefaultClassesDexName,
    resourcesApkName := DefaultResourcesApkName,
    dxOpts := DefaultDxOpts,
    manifestSchema := DefaultManifestSchema, 
    envs := DefaultEnvs 
  )
}
