package sbtandroid

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
  val versionName = TaskKey[String]("version-name")

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
  val assetsDirectoryName = SettingKey[String]("assets-dir-name")
  val resDirectoryName = SettingKey[String]("res-dir-name")
  val classesMinJarName = SettingKey[String]("classes-min-jar-name")
  val classesDexName = SettingKey[String]("classes-dex-name")
  val resourcesApkName = SettingKey[String]("resources-apk-name")
  val generatedProguardConfigName = SettingKey[String]("generated-proguard-config-name")
  val dxMemory = SettingKey[String]("dx-memory")
  val manifestSchema = SettingKey[String]("manifest-schema")
  val envs = SettingKey[Seq[String]]("envs")
  val preinstalledModules = SettingKey[Seq[ModuleID]]("preinstalled-modules")

  /** Determined Settings */
  val packageApkName = TaskKey[String]("package-apk-name")
  val packageApkLibName = TaskKey[String]("package-apklib-name")
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
  val manifestPackage = TaskKey[String]("manifest-package")
  val manifestPackageName = TaskKey[String]("manifest-package-name")
  val minSdkVersion = TaskKey[Option[Int]]("min-sdk-version")
  val maxSdkVersion = TaskKey[Option[Int]]("max-sdk-version")

  val manifestPath = TaskKey[Seq[File]]("manifest-path")
  val nativeLibrariesPath = SettingKey[File]("natives-lib-path")
  val jarPath = SettingKey[File]("jar-path")
  val mainAssetsPath = SettingKey[File]("main-asset-path")
  val mainResPath = TaskKey[File]("main-res-path")
  val managedJavaPath = SettingKey[File]("managed-java-path")
  val managedNativePath = SettingKey[File]("managed-native-path")
  val classesMinJarPath = SettingKey[File]("classes-min-jar-path")
  val classesDexPath = SettingKey[File]("classes-dex-path")
  val resourcesApkPath = SettingKey[File]("resources-apk-path")
  val generatedProguardConfigPath = SettingKey[File]("generated-proguard-config-path")
  val packageApkPath = TaskKey[File]("package-apk-path")
  val packageApkLibPath = TaskKey[File]("package-apklib-path")
  val useProguard = SettingKey[Boolean]("use-proguard")
  val buildConfigDebug = SettingKey[Boolean]("build-config-debug")
  val skipScalaLibrary = SettingKey[Boolean]("skip-scala-library")
  val predexLibraries = SettingKey[Boolean]("predex-libraries")

  /** Install Settings */
  val packageConfig = TaskKey[ApkConfig]("package-config",
    "Generates a Apk Config")

  /** Typed Resource Settings */
  val managedScalaPath = SettingKey[File]("managed-scala-path")
  val typedResource = TaskKey[File]("typed-resource",
    """Typed resource file to be generated, also includes
       interfaces to access these resources.""")
  val layoutResources = TaskKey[Seq[File]]("layout-resources", 
      """All files that are in res/layout. They will
		 be accessable through TR.layouts._""")

  /** Market Publish Settings */
  val keystorePath = SettingKey[File]("key-store-path")
  val zipAlignPath = SettingKey[File]("zip-align-path", "Path to zipalign")
  val packageAlignedName = TaskKey[String]("package-aligned-name")
  val packageAlignedPath = TaskKey[File]("package-aligned-path")

  /** Manifest Generator */
  val manifestTemplateName = SettingKey[String]("manifest-template-name")
  val manifestTemplatePath = SettingKey[File]("manifest-template-path")

  /** Base Tasks */
  case class LibraryProject(pkgName: String, manifest: File, sources: Set[File], resDir: Option[File], assetsDir: Option[File])

  val apklibPackage = TaskKey[File]("apklib-package")
  val extractApkLibDependencies = TaskKey[Seq[LibraryProject]]("apklib-dependencies", "Unpack apklib dependencies")
  val copyNativeLibraries = TaskKey[Unit]("copy-native-libraries", "Copy native libraries added to libraryDependencies")

  val apklibSources = TaskKey[Seq[File]]("apklib-sources", "Enumerate Java sources from apklibs")
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
  val dxInputs = TaskKey[Seq[File]]("dx-inputs", "Input for dex command")
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
  val generateManifest = TaskKey[Seq[File]]("generate-manifest",
    """Generates a customized AndroidManifest.xml with
       current build number and debug settings.""")

  /** Test Project Tasks */
  val testRunner       = TaskKey[String]("test-runner", "get the current test runner")
  val testEmulator     = TaskKey[Unit]("test-emulator", "runs tests in emulator")
  val testDevice       = TaskKey[Unit]("test-device",   "runs tests on device")
  val testOnlyEmulator = InputKey[Unit]("test-only-emulator", "run a single test on emulator")
  val testOnlyDevice   = InputKey[Unit]("test-only-device",   "run a single test on device")

  /** Github tasks & keys */
  val uploadGithub = TaskKey[Option[String]]("github-upload", "Upload file to github")
  val deleteGithub = TaskKey[Unit]("github-delete", "Delete file from github")
  val githubRepo   = SettingKey[String]("github-repo", "Github repo")

  val cachePasswords = SettingKey[Boolean]("cache-passwords", "Cache passwords")
  val clearPasswords = TaskKey[Unit]("clear-passwords", "Clear cached passwords")

  /** Advanced device manipulations **/
  val rootDevice = TaskKey[Unit]("root-device")
  val remountDevice = TaskKey[Unit]("remount-device")
  val rootEmulator = TaskKey[Unit]("root-emulator")
  val remountEmulator = TaskKey[Unit]("remount-emulator")

  /** Install Scala on device/emulator **/
  val preloadDevice     = TaskKey[Unit]("preload-device", "Setup device for development by uploading the predexed Scala library")
  val preloadEmulator   = InputKey[Unit]("preload-emulator", "Setup emulator for development by uploading the predexed Scala library")

  /** Unload Scala from device/emulator **/
  val unloadDevice   = TaskKey[Unit]("unload-device", "Unloads the Scala library from the device")
  val unloadEmulator = InputKey[Unit]("unload-emulator", "Unloads the Scala library from the emulator")

  /** Use preloaded Scala for development **/
  val usePreloadedScala = SettingKey[Boolean]("use-preloaded-scala",
    "If true, will preload the current Scala version on the device or emulator and use it for development")
}
