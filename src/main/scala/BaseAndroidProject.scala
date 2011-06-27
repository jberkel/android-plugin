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

  // Determined on OS
  val packageApkName = SettingKey[String]("package-apk-name")
  val osDxName = SettingKey[String]("os-dx-name")

  // Override this setting
  val platformName = SettingKey[String]("platform-name", "Targetted android platform")

  // Determined Settings 
  val manifestPackage = SettingKey[String]("manifest-package")
  val minSdkVersion = SettingKey[Option[Int]]("min-sdk-version")
  val maxSdkVersion = SettingKey[Option[Int]]("max-sdk-version")
  val apiLevel = SettingKey[Int]("api-level")

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

  private def isWindows = System.getProperty("os.name").startsWith("Windows")
  private def osBatchSuffix = if (isWindows) ".bat" else ""

  private def dxMemoryParameter(javaOpts: String) = {
    // per http://code.google.com/p/android/issues/detail?id=4217, dx.bat
    // doesn't currently support -JXmx arguments.  For now, omit them in windows.
    if (isWindows) "" else javaOpts
  }

  private def platformName2ApiLevel(pName: String) = pName match {
    case "android-1.0" => 1
    case "android-1.1" => 2
    case "android-1.5" => 3
    case "android-1.6" => 4
    case "android-2.0" => 5
    case "android-2.1" => 7
    case "android-2.2" => 8
    case "android-2.3" => 9
    case "android-2.3.3" => 10
    case "android-3.0" => 11
  }

  private def manifest(mpath: File) = xml.XML.loadFile(mpath)
  private def usesSdk(mpath: File, schema: String, key: String) = 
    (manifest(mpath) \ "uses-sdk").head.attribute(schema, key).map(_.text.toInt)

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

    packageApkName <<= (artifact) (_.name + ".apk"),
    osDxName <<= (dxName) (_ + osBatchSuffix),

    apiLevel <<= (minSdkVersion, platformName) { (min, pName) =>
      min.getOrElse(platformName2ApiLevel(pName))
    },
    manifestPackage <<= (manifestPath) {
      manifest(_).attribute("package").getOrElse(error("package not defined")).text
    },
    minSdkVersion <<= (manifestPath, manifestSchema)(usesSdk(_, _, "minSdkVersion")),
    maxSdkVersion <<= (manifestPath, manifestSchema)(usesSdk(_, _, "maxSdkVersion")),

    toolsPath <<= (sdkPath) (_ / "tools"),
    dbPath <<= (platformToolsPath, dbName) (_ / _),
    platformPath <<= (sdkPath, platformName) (_ / "platforms" / _),
    platformToolsPath <<= (sdkPath) (_ / "platform-tools"),
    aptPath <<= (platformToolsPath, aptName) (_ / _),
    idlPath <<= (platformToolsPath, idlName) (_ / _),
    dxPath <<= (platformToolsPath, osDxName) (_ / _),
    manifestPath <<= (sourceDirectory, manifestName) (_ / _),
    jarPath <<= (platformPath, jarName) (_ / _),

    sdkPath <<= (envs) { es => 
      determineAndroidSdkPath(es).getOrElse(error(
        "Android SDK not found. You might need to set %s".format(es.mkString(" or "))
      ))
    }
  ))
}
