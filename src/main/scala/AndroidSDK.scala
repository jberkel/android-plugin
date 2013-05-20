package org.scalasbt.androidplugin

import sbt._

import Keys._
import AndroidPlugin._
import AndroidHelpers._

object AndroidSDK {
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
  val DefaultManifestSchema = "http://schemas.android.com/apk/res/android"
  val DefaultEnvs = List("ANDROID_SDK_HOME", "ANDROID_SDK_ROOT", "ANDROID_HOME")

  lazy val settings: Seq[Setting[_]] = {Seq(
    // Command executable names
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
    manifestSchema := DefaultManifestSchema,
    envs := DefaultEnvs,

    // On Windows, `dx` is a .bat executable, but not on Linux
    osDxName <<= (dxName) (_ + osBatchSuffix),

    // Find the path of the Android SDK
    sdkPath <<= (envs, baseDirectory) ((e, d) => determineAndroidSdkPath(e, d)),

    // Generate the full paths of the SDK tools
    toolsPath <<= (sdkPath) (_ / "tools"),
    dbPath <<= (platformToolsPath, adbName) (_ / _),
    platformToolsPath <<= (sdkPath) (_ / "platform-tools"),
    aaptPath <<= (platformToolsPath, aaptName) (_ / _),
    idlPath <<= (platformToolsPath, aidlName) (_ / _),
    dxPath <<= (platformToolsPath, osDxName) (_ / _),

    // A list of modules which are already included in Android
    preinstalledModules := Seq[ModuleID](
      ModuleID("org.apache.httpcomponents", "httpcore", null),
      ModuleID("org.apache.httpcomponents", "httpclient", null),
      ModuleID("org.json", "json" , null),
      ModuleID("commons-logging", "commons-logging", null),
      ModuleID("commons-codec", "commons-codec", null)
    )
  )}
}
