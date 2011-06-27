import sbt._
import Keys._

object BaseAndroidProject extends Plugin {
  val Android = config("android")

  /** Default Settings */
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

  // Override this setting
  val platformName = SettingKey[String]("platform-name", "Targetted android platform")

  // Determined Settings 
  val sdkPath = SettingKey[File]("sdk-path")
  val toolsPath = SettingKey[File]("tools-path")
  val dbPath = SettingKey[File]("db-path")
  val platformPath = SettingKey[File]("platform-path")
  val platformToolsPath = SettingKey[File]("platform-tools-path")
  val aptPath = SettingKey[File]("apt-path")
  val idlPath = SettingKey[File]("idl-path")
  val dxPath = SettingKey[File]("dx-path")

  val manifestPath = SettingKey[File]("manifest-path")
  val jarPath = SettingKey[File]("jar-path")
  val addonsPath = SettingKey[File]("addons-path")
  val mapsJarPath = SettingKey[File]("maps-jar-path")
  val mainAssetPath = SettingKey[File]("main-asset-path")

  // Helpers
  private def determineAndroidSdkPath(es: Seq[String]) = {
    val paths = for ( e <- es; p = System.getenv(e); if p != null) yield p
    if (paths.isEmpty) None else Some(Path(paths.head).asFile)
  }

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
    envs := Seq("ANDROID_SDK_HOME", "ANDROID_SDK_ROOT", "ANDROID_HOME"),

    toolsPath <<= (sdkPath) (_ / "tools"),
    dbPath <<= (platformToolsPath, dbName) (_ / _),
    platformPath <<= (sdkPath, platformName) (_ / "platforms" / _),
    platformToolsPath <<= (sdkPath) (_ / "platform-tools"),
    aptPath <<= (platformToolsPath, aptName) (_ / _),
    idlPath <<= (platformToolsPath, idlName) (_ / _),
    dxPath <<= (platformToolsPath, dxName) (_ / _),
    manifestPath <<= (sourceDirectory, manifestName) (_ / _),
    jarPath <<= (platformPath, jarName) (_ / _),

    sdkPath <<= (envs) { es => 
      determineAndroidSdkPath(es).getOrElse(error(
        "Android SDK not found. You might need to set %s".format(es.mkString(" or "))
      ))
    }
  ))
}
