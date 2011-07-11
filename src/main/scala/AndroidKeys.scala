import sbt._

import Keys._

/*!# Android Keys
`AndroidKeys` contains all the `SettingKey`s and `TaskKey`s for a standard
Android project. 
 */
object AndroidKeys {
  val AndroidConfig = config("android") extend (Compile)

  /** Proguard Settings */
  val proguardOption = SettingKey[String]("proguard-option")
  val libraryJarPath = SettingKey[Seq[File]]("library-path") 

  /** Default Settings */
  val aaptName = SettingKey[String]("aapt-name")
  val adbName = SettingKey[String]("adb-name")
  val aidlName = SettingKey[String]("aidl-name")
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
  val aaptPath = SettingKey[File]("apt-path")
  val idlPath = SettingKey[File]("idl-path")
  val dxPath = SettingKey[File]("dx-path")

  val manifestPath = SettingKey[File]("manifest-path")
  val jarPath = SettingKey[File]("jar-path")
  val nativeLibrariesPath = SettingKey[File]("natives-lib-path")
  val addonsPath = SettingKey[File]("addons-path")
  val mapsJarPath = SettingKey[File]("maps-jar-path")
  val mainAssetsPath = SettingKey[File]("main-asset-path")
  val mainResPath = SettingKey[File]("main-res-path")
  val managedJavaPath = SettingKey[File]("managed-java-path")
  val classesMinJarPath = SettingKey[File]("classes-min-jar-path")
  val classesDexPath = SettingKey[File]("classes-dex-path")
  val resourcesApkPath = SettingKey[File]("resources-apk-path")
  val packageApkPath = SettingKey[File]("package-apk-path")
  val skipProguard = SettingKey[Boolean]("skip-proguard")

  val addonsJarPath = SettingKey[Seq[File]]("addons-jar-path")

  /** Proguard Tasks */
  val proguardInJars = TaskKey[Seq[File]]("proguard-in-jars")
  val proguardExclude = TaskKey[Seq[File]]("proguard-exclude") 

  /** General Tasks */
  val aptGenerate = TaskKey[Unit]("apt-generate")
  val aidlGenerate = TaskKey[Unit]("aidl-generate")

  /** Installable Tasks */
  val installEmulator = TaskKey[Unit]("install-emulator")
  val uninstallEmulator = TaskKey[Unit]("uninstall-emulator")

  val installDevice = TaskKey[Unit]("install-device")
  val uninstallDevice = TaskKey[Unit]("uninstall-device")

  val reinstallEmulator = TaskKey[Unit]("reinstall-emulator")
  val reinstallDevice = TaskKey[Unit]("reinstall-device")

  val aaptPackage = TaskKey[Unit]("aapt-package", "Package resources and assets.")
  val packageDebug = TaskKey[Unit]("package-debug", "Package and sign with a debug key.")
  val packageRelease = TaskKey[Unit]("package-release", "Package without signing.")

  val proguard = TaskKey[Unit]("proguard", "Optimize class files.")
  val dx = TaskKey[Unit]("dx", "Convert class files to dex files")

  /** Startable Tasks */
  val startDevice = TaskKey[Unit]("start-device", "Start package on device after installation")
  val startEmulator = TaskKey[Unit]("start-emulator", "Start package on emulator after installation")

  /** ddm Support tasks */
  val screenshotEmulator = TaskKey[File]("screenshot-emulator", "Take a screenshot from the emulator")
  val screenshotDevice = TaskKey[File]("screenshot-device", "Take a screenshot from the device")
}
