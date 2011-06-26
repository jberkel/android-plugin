import sbt._
import Keys._

object BaseAndroidProject extends Plugin {
  val Android = config("android")

  val aptName = SettingKey[String]("apt-name")
  val dbName = SettingKey[String]("db-name")
  val idlName = SettingKey[String]("idl-name")
  val dxName = SettingKey[String]("dx-name")
  val manifestName = SettingKey[String]("manifest-name")
  val jarName = SettingKey[String]("jar-name")
  val mapsJarName = SettingKey[String]("maps-jar-name")
  val assetsDirectoryName = SettingKey[String]("assets-dir-name")
  val resDirectoryName = SettingKey[String]("res-dir-name")
  val classesMinJarName = SettingKey[String]("classes-min-jar-name")
  val classesDexName = SettingKey[String]("classes-dex-name")
  val resourcesApkName = SettingKey[String]("resources-apk-name")
  val dxJavaOpts = SettingKey[String]("dx-java-opts")
  val manifestSchema = SettingKey[String]("manifest-schema")
  val envs = SettingKey[Seq[String]]("envs")

  override val settings = inConfig(Android) (Seq (
    aptName := "aapt",
    dbName := "adb",
    idlName := "aidl",
    dxName := "dx",
    manifestName := "AndroidManifest.xml",
    jarName := "android.jar",
    mapsJarName := "maps.jar",
    assetsDirectoryName := "assets",
    resDirectoryName := "res",
    classesMinJarName := "classes.min.jar",
    classesDexName := "classes.dex",
    resourcesApkName := "resources.apk",
    dxJavaOpts := "-JXmx512m",
    manifestSchema := "http://schemas.android.com/apk/res/android",
    envs := Seq("ANDROID_SDK_HOME", "ANDROID_SDK_ROOT", "ANDROID_HOME")
  ))
}
