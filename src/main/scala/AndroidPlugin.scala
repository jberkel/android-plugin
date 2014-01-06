package sbtandroid

import sbt._
import Keys._
import Defaults._

import AndroidHelpers.isWindows
import complete.DefaultParsers._
import scala.xml.transform.RewriteRule

object AndroidPlugin extends Plugin {

  /***************************************
   * Default configurations and projects *
   ***************************************/

  // Standard configurations
  val Preload = config("preload")
  val Release = config("release")

  // Android default targets
  val Target = AndroidDefaultTargets

  // Standard projects
  val AndroidProject = AndroidProjects.Standard
  val AndroidTestProject = AndroidProjects.Test

  // Standard configurations
  lazy val androidTest = AndroidTestProject.defaults
  lazy val androidDefaults = AndroidProject.defaults

  // Additional configuration for those using Java/Ant projects
  lazy val androidJavaLayout: Seq[Setting[_]] =
    AndroidJavaLayout.settings

  // Android SDK and emulator tasks/settings will be automatically loaded
  // for every project.
  override lazy val settings: Seq[Setting[_]] =
    AndroidPath.settings ++ AndroidEmulator.settings

  /******************
   * Helper methods *
   ******************/

  // ApkLib and AAR artifact definitions
  def apklib(module: ModuleID) = module artifacts(Artifact(module.name, "apklib", "apklib"))
  def aarlib(module: ModuleID) = module artifacts(Artifact(module.name, "aar", "aar"))

  // Common module filters
  def filterFilename(filename: String) = (f: Attributed[File]) => f.data.name contains filename
  def filterFile(file: File) = (f: Attributed[File]) => f.data == file
  def filterName(name: String) = (f: Attributed[File]) => f.get(moduleID.key) match {
    case Some(n) => n.name contains name
    case None => false
  }
  def filterModule(module: ModuleID) = (f: Attributed[File]) => f.get(moduleID.key) match {
    case Some(m) => m == module
    case None => false
  }

  /**********************
   * Public plugin keys *
   **********************/

  /** Android target **/
  val adbTarget = SettingKey[AndroidTarget]("adb-target", "Current Android target (device or emulator) connected to ADB")

  /** User Defines */
  val platformName = SettingKey[String]("platform-name", "Targetted android platform")
  val keyalias = SettingKey[String]("key-alias")
  val versionCode = SettingKey[Int]("version-code")
  val versionName = TaskKey[String]("version-name")

  /** Packaging settings **/
  val useProguard = SettingKey[Boolean]("use-proguard", "Use Proguard to package the app")
  val usePreloaded = SettingKey[Boolean]("use-preloaded", "Use preloaded libraries for development")
  val useDebug = SettingKey[Boolean]("use-debug", "Use debug settings when building an APK")
  val useTypedResources = SettingKey[Boolean]("use-typed-resources", "Use typed resources")
  val useTypedLayouts = SettingKey[Boolean]("use-typed-layouts", "Use typed layouts")

  /** ApkLib dependencies */
  case class LibraryProject(pkgName: String, manifest: File, sources: Set[File], resDir: Option[File], assetsDir: Option[File])
  val apklibPackage = TaskKey[File]("apklib-package")
  val apklibDependencies = TaskKey[Seq[LibraryProject]]("apklib-dependencies", "Unpack apklib dependencies")
  val apklibBaseDirectory = SettingKey[File]("apklib-base-directory", "Base directory for the ApkLib dependencies")
  val apklibSourceManaged = SettingKey[File]("apklib-source-managed", "Base directory for the ApkLib sources")
  val apklibResourceManaged = SettingKey[File]("apklib-resource-managed", "Base directory for the resources included in the ApkLibs")
  val apklibSources = TaskKey[Seq[File]]("apklib-sources", "Enumerate Java sources from apklibs")

  /** AAR dependencies */
  val aarlibDependencies = TaskKey[Seq[LibraryProject]]("aarlib-dependencies", "Unpack aarlib dependencies")
  val aarlibBaseDirectory = SettingKey[File]("aarlib-base-directory", "Base directory for the aarLib dependencies")
  val aarlibLibManaged = SettingKey[File]("aarlib-lib-managed", "Base directoyr for the aarLib JAR libraries")
  val aarlibResourceManaged = SettingKey[File]("aarlib-resource-managed", "Base directory for the resources included in the aarLibs")

  /** General inputs for the APK **/
  val inputClasspath = TaskKey[Seq[File]]("input-classpath", "All the classpath entries needed by the APK")
  val includedClasspath = TaskKey[Seq[File]]("included-classpath", "Classpath entries included in the final APK")
  val providedClasspath = TaskKey[Seq[File]]("provided-classpath", "Classpath entries provided by the running target")

  /** Proguard Settings **/
  val proguardInJarsFilter = SettingKey[File => Traversable[String]]("proguard-in-jars-filter")
  val proguardOptions = SettingKey[Seq[String]]("proguard-options")
  val proguardOptimizations = SettingKey[Seq[String]]("proguard-optimizations")
  val proguardOutputPath = SettingKey[File]("proguard-output-path", "Path to Proguard's output JAR")
  val proguardConfiguration = TaskKey[Option[File]]("proguard-configuration", "Path to the Proguard configuration file")
  val proguard = TaskKey[Option[File]]("proguard", "Run Proguard on the class files")

  /** Dexing **/
  val dxOutputPath = SettingKey[File]("dx-output-path")
  val dxInputs = TaskKey[Seq[File]]("dx-inputs", "Input class files included in the final APK")
  val dxPredex = TaskKey[Seq[File]]("dx-predex", "Paths that will be predexed before generating the final DEX")
  val dx = TaskKey[File]("dx", "Convert class files to DEX files")

  /** APK Generation **/
  val apk = TaskKey[File]("apk", "Package and sign with a debug key.")
  val aaptPackage = TaskKey[File]("aapt-package", "Package resources and assets.")
  val cleanApk = TaskKey[Unit]("clean-apk", "Remove apk package")

  /** Install Scala on device/emulator **/
  val preloadFilters     = SettingKey[Seq[Attributed[File] => Boolean]]("preload-filters", "Filters the libraries that are to be preloaded")
  val preloadDevice      = TaskKey[Unit]("preload-device", "Setup device for development by uploading predexed libraries")
  val preloadEmulator    = InputKey[Unit]("preload-emulator", "Setup emulator for development by uploading predexed libraries")

  /** Installable Tasks */
  val install = TaskKey[Unit]("install")
  val uninstall = TaskKey[Unit]("uninstall")

  /** Launch Tasks */
  val start = TaskKey[Unit]("start", "Start package on device after installation")
  val debug = TaskKey[Unit]("debug", "Start package on device after installation, and wait for a debugger to attach")

  /** Modules that are preloaded on the device **/
  val preinstalledModules = SettingKey[Seq[ModuleID]]("preinstalled-modules")

  /** Default Settings */
  val adbName = SettingKey[String]("adb-name", "Name of the ADB command")
  val aaptName = SettingKey[String]("aapt-name")
  val aidlName = SettingKey[String]("aidl-name")
  val dxName = SettingKey[String]("dx-name")
  val manifestName = SettingKey[String]("manifest-name")
  val libraryJarName = SettingKey[String]("library-jar-name")
  val assetsDirectoryName = SettingKey[String]("assets-dir-name")
  val resDirectoryName = SettingKey[String]("res-dir-name")
  val classesMinJarName = SettingKey[String]("classes-min-jar-name")
  val classesDexName = SettingKey[String]("classes-dex-name")
  val resourcesApkName = SettingKey[String]("resources-apk-name")
  val generatedProguardConfigName = SettingKey[String]("generated-proguard-config-name")
  val dxMemory = SettingKey[String]("dx-memory")
  val manifestSchema = SettingKey[String]("manifest-schema")
  val envs = SettingKey[Seq[String]]("envs")
  val packageApkName = TaskKey[String]("package-apk-name")
  val packageApkLibName = TaskKey[String]("package-apklib-name")
  val osDxName = SettingKey[String]("os-dx-name")

  /** Path Settings */
  val sdkPath = SettingKey[File]("sdk-path")
  val platformToolsPath = SettingKey[File]("platform-tools-path")
  val buildToolsVersion = SettingKey[Option[String]]("build-tools-version")
  val buildToolsPath = SettingKey[File]("build-tools-path")
  val toolsPath = SettingKey[File]("tools-path")
  val dbPath = SettingKey[File]("db-path")
  val aaptPath = SettingKey[File]("apt-path")
  val idlPath = SettingKey[File]("idl-path")
  val dxPath = SettingKey[File]("dx-path")
  val libraryJarPath = SettingKey[File]("library-jary-path")

  /** Base app manifest settings */
  val platformPath = SettingKey[File]("platform-path")
  val manifestPackage = TaskKey[String]("manifest-package")
  val manifestPackageName = TaskKey[String]("manifest-package-name")
  val minSdkVersion = TaskKey[Option[Int]]("min-sdk-version")
  val maxSdkVersion = TaskKey[Option[Int]]("max-sdk-version")

  /** Project paths */
  val manifestPath = TaskKey[Seq[File]]("manifest-path")
  val mainAssetsPath = SettingKey[File]("main-asset-path")
  val mainResPath = TaskKey[File]("main-res-path")
  val resPath = TaskKey[Seq[File]]("res-path")
  val managedJavaPath = SettingKey[File]("managed-java-path")
  val managedScalaPath = SettingKey[File]("managed-scala-path")
  val resourcesApkPath = SettingKey[File]("resources-apk-path")
  val generatedProguardConfigPath = SettingKey[File]("generated-proguard-config-path")
  val packageApkPath = TaskKey[File]("package-apk-path")
  val packageApkLibPath = TaskKey[File]("package-apklib-path")

  /** Native libraries */
  val unmanagedNativePath = SettingKey[File]("unmanaged-native-path")
  val managedNativePath = SettingKey[File]("managed-native-path")
  val nativeDirectories = TaskKey[Seq[File]]("native-directories")

  /** Install Settings */
  val packageConfig = TaskKey[ApkConfig]("package-config",
    "Generates the APK configuration")

  /** Typed Resource Settings */
  val typedResource = TaskKey[File]("typed-resource",
    """Typed resource file to be generated, also includes
       interfaces to access these resources.""")
  val typedLayouts = TaskKey[File]("typed-layouts",
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

  val copyNativeLibraries = TaskKey[Unit]("copy-native-libraries", "Copy native libraries added to libraryDependencies")

  /*********************
   * Source generators *
   *********************/

  val aaptGenerate = TaskKey[Seq[File]]("aapt-generate", "Generate R.java")
  val aidlGenerate = TaskKey[Seq[File]]("aidl-generate",
    "Generate Java classes from .aidl files.")
  val generateTypedResources = TaskKey[Seq[File]]("generate-typed-resources",
    """Produce a file TR.scala that contains typed
       references to layout resources.""")
  val generateTypedLayouts = TaskKey[Seq[File]]("generate-typed-layouts",
    """Produce a file typed_resource.scala that contains typed
       references to layout resources.""")
  val generateManifest = TaskKey[Seq[File]]("generate-manifest",
    """Generates a customized AndroidManifest.xml with
       current build number and debug settings.""")

  /**********************
   * Manifest generator *
   **********************/

  val manifestTemplateName = SettingKey[String]("manifest-template-name")
  val manifestTemplatePath = SettingKey[File]("manifest-template-path")
  val manifestRewriteRules = TaskKey[Seq[RewriteRule]]("manifest-rewrite-rules",
    "Rules for transforming the contents of AndroidManifest.xml based on the project state and settings.")

  /*******************
   * Debugging tasks *
   *******************/

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

  /***********************
   * Store release tasks *
   ***********************/

  val release = TaskKey[File]("release", "Prepare a release APK for Store publication.")
  val zipAlign = TaskKey[File]("zip-align", "Run zipalign on signed jar.")
  val signRelease = TaskKey[File]("sign-release", "Sign with key alias using key-alias and keystore path.")
  val cleanAligned = TaskKey[Unit]("clean-aligned", "Remove zipaligned jar")

  /******************
   * Emulator tasks *
   ******************/

  val emulatorStart = InputKey[Unit]("emulator-start",
    "Launches a user specified avd")
  val emulatorStop = TaskKey[Unit]("emulator-stop",
    "Kills the running emulator.")
  val listDevices = TaskKey[Unit]("list-devices",
    "List devices from the adb server.")
  val killAdb = TaskKey[Unit]("kill-server",
    "Kill the adb server if it is running.")

  /**************
   * Test tasks *
   **************/

  val testRunner       = TaskKey[String]("test-runner", "get the current test runner")
  val testEmulator     = TaskKey[Unit]("test-emulator", "runs tests in emulator")
  val testDevice       = TaskKey[Unit]("test-device",   "runs tests on device")
  val testOnlyEmulator = InputKey[Unit]("test-only-emulator", "run a single test on emulator")
  val testOnlyDevice   = InputKey[Unit]("test-only-device",   "run a single test on device")

  /********************
   * Password managed *
   ********************/

  val cachePasswords = SettingKey[Boolean]("cache-passwords", "Cache passwords")
  val clearPasswords = TaskKey[Unit]("clear-passwords", "Clear cached passwords")

  /********************
   * Android NDK keys *
   ********************/

  val ndkBuildName = SettingKey[String]("ndk-build-name", "Name for the 'ndk-build' tool")
  val ndkBuildPath = SettingKey[Option[File]]("ndk-build-path", "Path to the 'ndk-build' tool")

  val ndkLibDirectoryName =  SettingKey[String]("ndk-lib-directory-name", "Directory name for compiled native libraries.")
  val ndkJniDirectoryName = SettingKey[String]("ndk-jni-directory-name", "Directory name for native sources.")
  val ndkObjDirectoryName =  SettingKey[String]("ndk-obj-directory-name", "Directory name for compiled native objects.")
  val ndkUnmanagedEnv = SettingKey[String]("ndk-unmanaged-env",
      "Name of the make environment variable to bind to the unmanaged-base directory")
  val ndkEnvs = SettingKey[Seq[String]]("ndk-envs", "List of environment variables to check for the NDK.")

  val ndkJniSourcePath = SettingKey[File]("jni-source-path", "Path to native sources. (with Android.mk)")
  val ndkNativeOutputPath = SettingKey[File]("native-output-path", "NDK output path")
  val ndkNativeObjectPath = SettingKey[File]("native-object-path", "Path to the compiled native objects")

  val ndkBuild = TaskKey[Seq[File]]("ndk-build", "Compile native C/C++ sources.")
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

  /************************
   * IntelliJ integration *
   ************************/

   val ideaConfiguration = SettingKey[Configuration]("idea-configuration", "Configuration used by sbtidea to generate the IntelliJ project")
}
