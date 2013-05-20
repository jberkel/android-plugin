package org.scalasbt.androidplugin

import sbt._
import Keys._
import Defaults._

import AndroidHelpers.isWindows
import complete.DefaultParsers._
import scala.xml.transform.RewriteRule

object AndroidPlugin extends Plugin {

  /***************************
   * Default plugin settings *
   ***************************/

  // Default Android settings for standard projects
  // Standard presets :
  //
  //  * androidDevelopment:
  //        Does not include the Scala library, skips Proguard,
  //        predexes external libraries and automatically sets
  //        AndroidManifest.xml to require a preloaded Scala library.
  //
  //        NOTE: The generated APK will NOT be compatible with stock devices
  //              without a preloaded Scala library!
  //
  //  * androidDebug: Will generate a debug APK compatible with stock Android devices.
  //  * androidRelease: Will generate a realeas APK compatible with stock Android devices.
  //
  // Advanced presets :
  //
  //  * androidDefaults: Base for all the other presets
  //  * androidTest: Additional settings for test projects
  //  * androidNdk: Additional settings for projects using the NDK

  // Base defaults
  lazy val androidDefaults: Seq[Setting[_]] =
    AndroidBase.settings ++
    AndroidManifestGenerator.settings ++
    AndroidPreload.settings ++
    AndroidInstall.settings ++
    AndroidLaunch.settings ++
    AndroidDdm.settings ++
    TypedResources.settings

  // Development settings
  lazy val androidDevelopment: Seq[Setting[_]] =
    androidDefaults ++ Seq(
      useProguard := false,
      usePreloadedScala := true,
      skipScalaLibrary := true,
      predexLibraries := true
    )

  // Debug settings
  lazy val androidDebug: Seq[Setting[_]] =
    androidDefaults ++ Seq(
      useProguard := true,
      usePreloadedScala := false,
      skipScalaLibrary := false,
      predexLibraries := true
    )

  // Release settings
  lazy val androidRelease: Seq[Setting[_]] =
    androidDefaults ++ Seq(
      useProguard := true,
      usePreloadedScala := false,
      skipScalaLibrary := false,
      predexLibraries := true
    )

  // Test settings
  lazy val androidTest: Seq[Setting[_]] =
    AndroidTest.settings

  // NDK settings
  lazy val androidNdk: Seq[Setting[_]] =
    AndroidNdk.settings

  /**
   * Configures a specific configuration to use SBT-Android.
   *
   * Example use:
   *
   *   One build type, development mode:
   *     Project("main", file(".")).settings(androidConfigure(androidDevelopment))
   *
   *   One development build and one release build:
   */
  def androidConfigure(androidSettings: Seq[Setting[_]], configuration: Configuration = Compile) = {
    inConfig(configuration) {
      // First, setup the configuration
      (
        // If the configuration is Compile, no need to do anything
        if (configuration == Compile) Seq.empty

        // If the configuration is anything else, we import the default
        // configuration tasks and settings (compile, run, sourceDirectory,...)
        else configSettings

      // And, last, add the Android settings
      ) ++ androidSettings
    }
  }

  // Android SDK and emulator tasks/settings will be automatically loaded
  // for every project.
  override lazy val settings: Seq[Setting[_]] =
    AndroidSDK.settings ++ AndroidEmulator.settings

  /**********************
   * Public plugin keys *
   **********************/

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

  /** Emulator/AVDs/ADB controls **/
  val emulatorStart = InputKey[Unit]("emulator-start",
    "Launches a user specified avd")
  val emulatorStop = TaskKey[Unit]("emulator-stop",
    "Kills the running emulator.")
  val listDevices = TaskKey[Unit]("list-devices",
    "List devices from the adb server.")
  val killAdb = TaskKey[Unit]("kill-server",
    "Kill the adb server if it is running.")

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

  /********************
   * Android NDK keys *
   ********************/

  val ndkBuildName = SettingKey[String]("ndk-build-name", "Name for the 'ndk-build' tool")
  val ndkBuildPath = SettingKey[File]("ndk-build-path", "Path to the 'ndk-build' tool")

  val ndkJniDirectoryName = SettingKey[String]("ndk-jni-directory-name", "Directory name for native sources.")
  val ndkObjDirectoryName =  SettingKey[String]("ndk-obj-directory-name", "Directory name for compiled native objects.")
  val ndkEnvs = SettingKey[Seq[String]]("ndk-envs", "List of environment variables to check for the NDK.")

  val ndkJniSourcePath = SettingKey[File]("jni-source-path", "Path to native sources. (with Android.mk)")
  val ndkNativeOutputPath = SettingKey[File]("native-output-path", "NDK output path")
  val ndkNativeObjectPath = SettingKey[File]("native-object-path", "Path to the compiled native objects")

  val ndkBuild = TaskKey[Unit]("ndk-build", "Compile native C/C++ sources.")
  val ndkClean = TaskKey[Unit]("ndk-clean", "Clean resources built from native C/C++ sources.")

  /**************
   * Javah keys *
   **************/

  val javahName = SettingKey[String]("javah-name", "The name of the javah command for generating JNI headers")
  val javahPath = SettingKey[String]("javah-path", "The path to the javah executable")
  val javah = TaskKey[Unit]("javah", "Produce C headers from Java classes with native methods")
  val javahClean = TaskKey[Unit]("javah-clean", "Clean C headers built from Java classes with native methods")

  val javahOutputDirectory = SettingKey[File]("javah-output-directory",
      "The directory where JNI headers are written to.")
  val javahOutputFile = SettingKey[Option[File]]("javah-output-file",
      "filename for the generated C header, relative to javah-output-directory")
  val javahOutputEnv = SettingKey[String]("javah-output-env",
      "Name of the make environment variable to bind to the javah-output-directory")

  val jniClasses = SettingKey[Seq[String]]("jni-classes",
      "Fully qualified names of classes with native methods for which JNI headers are to be generated.")

  /*****************
   * Auto-Manifest *
   *****************/

  val manifestTemplateName = SettingKey[String]("manifest-template-name")
  val manifestTemplatePath = SettingKey[File]("manifest-template-path")
  val manifestRewriteRules = TaskKey[Seq[RewriteRule]]("manifest-rewrite-rules",
    "Rules for transforming the contents of AndroidManifest.xml based on the project state and settings.")
}
