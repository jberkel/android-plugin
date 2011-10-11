import sbt._

import Keys._

/*!# Android Keys
`AndroidKeys` contains all the `SettingKey`s and `TaskKey`s for a standard
Android project.
 */
object AndroidKeys {
  val Android= config("android") extend (Compile)

  /** User Defines */
  val platformName = SettingKey[String]("platform-name", "Targetted android platform")
  val keyalias = SettingKey[String]("key-alias")
  val versionCode = SettingKey[Int]("version-code")

  /** Proguard Settings */
  val proguardOption = SettingKey[String]("proguard-option")
  val proguardOptimizations = SettingKey[Seq[String]]("proguard-optimizations")
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

  /** Determined Settings */
  val packageApkName = SettingKey[String]("package-apk-name")
  val osDxName = SettingKey[String]("os-dx-name")

  /** Path Settings */
  val sdkPath = SettingKey[File]("sdk-path")
  val toolsPath = SettingKey[File]("tools-path")
  val dbPath = SettingKey[File]("db-path")
  val platformPath = SettingKey[File]("platform-path")
  val aaptPath = SettingKey[File]("apt-path")
  val idlPath = SettingKey[File]("idl-path")
  val dxPath = SettingKey[File]("dx-path")

  /** Base Settings */
  val platformToolsPath = SettingKey[File]("platform-tools-path")
  val manifestPackage = SettingKey[String]("manifest-package")
  val minSdkVersion = SettingKey[Option[Int]]("min-sdk-version")
  val maxSdkVersion = SettingKey[Option[Int]]("max-sdk-version")
  val apiLevel = SettingKey[Int]("api-level")

  val manifestPath = SettingKey[File]("manifest-path")
  val nativeLibrariesPath = SettingKey[File]("natives-lib-path")
  val addonsPath = SettingKey[File]("addons-path")
  val jarPath = SettingKey[File]("jar-path")
  val mapsJarPath = SettingKey[File]("maps-jar-path")
  val mainAssetsPath = SettingKey[File]("main-asset-path")
  val mainResPath = SettingKey[File]("main-res-path")
  val managedJavaPath = SettingKey[File]("managed-java-path")
  val classesMinJarPath = SettingKey[File]("classes-min-jar-path")
  val classesDexPath = SettingKey[File]("classes-dex-path")
  val resourcesApkPath = SettingKey[File]("resources-apk-path")
  val packageApkPath = SettingKey[File]("package-apk-path")
  val useProguard = SettingKey[Boolean]("use-proguard")

  val addonsJarPath = SettingKey[Seq[File]]("addons-jar-path")

  /** Install Settings */
  val packageConfig = SettingKey[ApkConfig]("package-config",
    "Generates a Apk Config")

  /** Typed Resource Settings */
  val managedScalaPath = SettingKey[File]("managed-scala-path")
  val typedResource = SettingKey[File]("typed-resource",
    """Typed resource file to be generated, also includes
       interfaces to access these resources.""")
  val layoutResources = SettingKey[Seq[File]]("layout-resources")

  /** Market Publish Settings */
  val keystorePath = SettingKey[File]("key-store-path")
  val zipAlignPath = SettingKey[File]("zip-align-path", "Path to zipalign")
  val packageAlignedName = SettingKey[String]("package-aligned-name")
  val packageAlignedPath = SettingKey[File]("package-aligned-path")

  /** Manifest Generator */
  val manifestTemplateName = SettingKey[String]("manifest-template-name")
  val manifestTemplatePath = SettingKey[File]("manifest-template-path")

  /** Base Tasks */
  val aaptGenerate = TaskKey[Seq[File]]("aapt-generate", "Generate R.java")
  val aidlGenerate = TaskKey[Seq[File]]("aidl-generate",
    "Generate Java classes from .aidl files.")

  val proguardInJars = TaskKey[Seq[File]]("proguard-in-jars")
  val proguardExclude = TaskKey[Seq[File]]("proguard-exclude")

  val makeManagedJavaPath = TaskKey[Unit]("make-managed-java-path")

  /** Installable Tasks */
  val installEmulator = TaskKey[Unit]("install-emulator")
  val uninstallEmulator = TaskKey[Unit]("uninstall-emulator")

  val installDevice = TaskKey[Unit]("install-device")
  val uninstallDevice = TaskKey[Unit]("uninstall-device")

  val aaptPackage = TaskKey[File]("aapt-package",
    "Package resources and assets.")
  val packageDebug = TaskKey[File]("package-debug",
    "Package and sign with a debug key.")
  val packageRelease = TaskKey[File]("package-release", "Package without signing.")
  val cleanApk = TaskKey[Unit]("clean-apk", "Remove apk package")

  val proguard = TaskKey[Option[File]]("proguard", "Optimize class files.")
  val dx = TaskKey[File]("dx", "Convert class files to dex files")

  val makeAssetPath = TaskKey[Unit]("make-assest-path")

  /** Launch Tasks */
  val startDevice = TaskKey[Unit]("start-device",
    "Start package on device after installation")
  val startEmulator = TaskKey[Unit]("start-emulator",
    "Start package on emulator after installation")

  /** ddm Support tasks */
  val stopBridge = TaskKey[Unit]("stop-bridge",
    "Terminates the ADB debugging bridge")
  val screenshotEmulator = TaskKey[File]("screenshot-emulator",
    "Take a screenshot from the emulator")
  val screenshotDevice = TaskKey[File]("screenshot-device",
    "Take a screenshot from the device")

  // hprof tasks are Unit because of async nature
  val hprofEmulator = TaskKey[Unit]("hprof-emulator",
    "Take a dump of the current heap from the emulator")
  val hprofDevice = TaskKey[Unit]("hprof-device",
    "Take a dump of the current heap from the device")

  val threadsEmulator = InputKey[Unit]("threads-emulator",
    "Show thread dump from the emulator")
  val threadsDevice = InputKey[Unit]("threads-device",
    "Show thread dump from the device")

  /** Market Publish tasks */
  val prepareMarket = TaskKey[File]("prepare-market",
    "Prepare asset for Market publication.")
  val zipAlign = TaskKey[File]("zip-align", "Run zipalign on signed jar.")
  val signRelease = TaskKey[File]("sign-release",
    "Sign with key alias using key-alias and keystore path.")
  val cleanAligned = TaskKey[Unit]("clean-aligned", "Remove zipaligned jar")


  /** TypedResources Task */
  val generateTypedResources = TaskKey[Seq[File]]("generate-typed-resources",
    """Produce a file TR.scala that contains typed
       references to layout resources.""")

  /** Manifest Generator tasks*/
  val generateManifest = TaskKey[File]("generate-manifest",
    """Generates a customized AndroidManifest.xml with
       current build number and debug settings.""")

  /** Test Project Tasks */
  val testEmulator = TaskKey[Unit]("test-emulator", "runs tests in emulator")
  val testDevice = TaskKey[Unit]("test-device", "runs tests on device")
}
