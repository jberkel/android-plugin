import com.netgents.antelese.Antelese.{task => anttask,  _}
import io.Source
import java.io._
import sbt._
import Process._

object AndroidProject {
  val DefaultAaptName = "aapt"
  val DefaultAdbName = "adb"
  val DefaultAidlName = "aidl"
  val DefaultApkbuilderName = "apkbuilder"
  val DefaultDxName = "dx"
  val DefaultAndroidPlatformName = "android-1.5"
  val DefaultAndroidManifestName = "AndroidManifest.xml"
  val DefaultAndroidJarName = "android.jar"
  val DefaultMapsJarName = "maps.jar"  
  val DefaultAssetsDirectoryName = "assets"
  val DefaultResDirectoryName = "res"
  val DefaultClassesMinJarName = "classes.min.jar"
  val DefaultClassesDexName = "classes.dex"
  val DefaultResourcesApkName = "resources.apk"
}

abstract class AndroidProject(info: ProjectInfo) extends DefaultProject(info) {


  def proguardOption = ""
  def proguardInJars = runClasspath --- proguardExclude
  def proguardExclude = libraryJarPath +++ mainCompilePath +++ mainResourcesPath +++ managedClasspath(Configurations.Provided)
  def libraryJarPath = androidJarPath +++ addonsJarPath
  override def unmanagedClasspath = super.unmanagedClasspath +++ libraryJarPath
  

  import AndroidProject._

  def aaptName = DefaultAaptName
  def adbName = DefaultAdbName
  def aidlName = DefaultAidlName
  def apkbuilderName = DefaultApkbuilderName
  def dxName = DefaultDxName
  def androidPlatformName = DefaultAndroidPlatformName
  def androidManifestName = DefaultAndroidManifestName
  def androidJarName = DefaultAndroidJarName
  def mapsJarName = DefaultMapsJarName
  def assetsDirectoryName = DefaultAssetsDirectoryName
  def resDirectoryName = DefaultResDirectoryName
  def classesMinJarName = DefaultClassesMinJarName
  def classesDexName = DefaultClassesDexName
  def packageApkName = artifactBaseName + ".apk"
  def resourcesApkName = DefaultResourcesApkName

  def scalaHomePath = Path.fromFile(new File(System.getProperty("scala.home")))
  def androidSdkPath: Path
  def apiLevel = minSdkVersion.getOrElse(platformName2ApiLevel)
  
  def platformName2ApiLevel:Int = androidPlatformName match {
    case "android-1.0" => 1
    case "android-1.1" => 2
    case "android-1.5" => 3
    case "android-1.6" => 4
    case "android-2.0" => 5
  }

  
  def androidToolsPath = androidSdkPath / "tools"
  def apkbuilderPath = androidToolsPath / DefaultApkbuilderName
  def adbPath = androidToolsPath / adbName
  def androidPlatformPath = androidSdkPath / "platforms" / androidPlatformName
  def platformToolsPath = androidPlatformPath / "tools"
  def aaptPath = platformToolsPath / aaptName
  def aidlPath = platformToolsPath / aidlName
  def dxPath = platformToolsPath / DefaultDxName

  def androidManifestPath =  mainSourcePath / androidManifestName
  def androidJarPath = androidPlatformPath / androidJarName
  def addonsPath = androidSdkPath / "add-ons" / ("google_apis-" + apiLevel) / "libs"
  def mapsJarPath = addonsPath / DefaultMapsJarName
  def mainAssetsPath = mainSourcePath / assetsDirectoryName
  def mainResPath = mainSourcePath / resDirectoryName
  def classesMinJarPath = outputPath / classesMinJarName
  def classesDexPath =  outputPath / classesDexName
  def resourcesApkPath = outputPath / resourcesApkName
  def packageApkPath = outputPath / packageApkName

  lazy val aaptGenerate = aaptGenerateAction
  def aaptGenerateAction = aaptGenerateTask describedAs("Generate R.java.")
  def aaptGenerateTask = execTask {<x>
      {aaptPath.absolutePath} package -m -M {androidManifestPath.absolutePath} -S {mainResPath.absolutePath}
         -I {androidJarPath.absolutePath} -J {mainJavaSourcePath.absolutePath}
    </x>}

  lazy val aidl = aidlAction
  def aidlAction = aidlTask describedAs("Generate Java classes from .aidl files.")
  def aidlTask = execTask {
	val aidlPaths = descendents(mainSourceRoots, "*.aidl").getPaths
	if(aidlPaths.isEmpty)
		Process(true)
	else
  	{
		aidlPath.absolutePath ::
		"-o" ::
		mainJavaSourcePath.absolutePath ::
		aidlPaths.toList
	}
  }
  
  override def compileAction = super.compileAction dependsOn(aaptGenerate, aidl)
  
  lazy val proguard = proguardAction
  def proguardAction = proguardTask dependsOn(compile) describedAs("Optimize class files.")
  def proguardTask = task {
    taskdef('resource -> "proguard/ant/task.properties")
    anttask("proguard")('<> ->
      <a>
      -injars {mainCompilePath.absolutePath + File.pathSeparator + FileUtilities.scalaLibraryJar.getAbsolutePath}(!META-INF/MANIFEST.MF,!library.properties){proguardInJars.get.map(File.pathSeparator + _.absolutePath + "(!META-INF/MANIFEST.MF)")}
      -outjars {classesMinJarPath.absolutePath}
      -libraryjars {libraryJarPath.get.map(_.absolutePath).mkString(File.pathSeparator)}
      -dontwarn
      -dontoptimize
      -dontobfuscate
      -keep public class * extends android.app.Activity
      -keep public class * extends android.app.Service
      -keep public class * extends android.appwidget.AppWidgetProvider
      -keep public class * implements junit.framework.Test 
      {proguardOption}
      </a>.text)
    None
  }
  
  lazy val dx = dxAction
  def dxAction = dxTask dependsOn(proguard) describedAs("Convert class files to dex files")
  def dxTask = fileTask(classesDexPath from classesMinJarPath) { execTask {<x> {dxPath.absolutePath} -JXmx512M --dex --output={classesDexPath.absolutePath} {classesMinJarPath.absolutePath}</x> } run } 
  
  lazy val aaptPackage = aaptPackageAction
  def aaptPackageAction = aaptPackageTask dependsOn(dx) describedAs("Package resources and assets.")
  def aaptPackageTask = execTask {<x>
    {aaptPath.absolutePath} package -f -M {androidManifestPath.absolutePath} -S {mainResPath.absolutePath} 
       -A {mainAssetsPath.absolutePath} -I {androidJarPath.absolutePath} -F {resourcesApkPath.absolutePath}
  </x>}

  lazy val packageDebug = packageDebugAction
  def packageDebugAction = packageTask(true) dependsOn(aaptPackage) describedAs("Package and sign with a debug key.")

  lazy val packageRelease = packageReleaseAction
  def packageReleaseAction = packageTask(false) dependsOn(aaptPackage) describedAs("Package without signing.")

  lazy val cleanApk = cleanTask(packageApkPath) describedAs("Remove apk package")
  def packageTask(signPackage: Boolean) = execTask {<x>
      {apkbuilderPath.absolutePath}  {packageApkPath.absolutePath}
        {if (signPackage) "" else "-u"} -z {resourcesApkPath.absolutePath} -f {classesDexPath.absolutePath}
        {proguardInJars.get.map(" -rj " + _.absolutePath)}
  </x>} dependsOn(cleanApk)
  
  lazy val installEmulator = installEmulatorAction
  def installEmulatorAction = installTask(true) dependsOn(packageDebug) describedAs("Install package on the default emulator.")

  lazy val installDevice = installDeviceAction
  def installDeviceAction = installTask(false) dependsOn(packageDebug) describedAs("Install package on the default device.")

  lazy val reinstallEmulator = reinstallEmulatorAction
  def reinstallEmulatorAction = reinstallTask(true) dependsOn(packageDebug) describedAs("Reinstall package on the default emulator.")

  lazy val reinstallDevice = reinstallDeviceAction
  def reinstallDeviceAction = reinstallTask(false) dependsOn(packageDebug) describedAs("Reinstall package on the default device.")

  lazy val uninstallEmulator = uninstallEmulatorAction
  def uninstallEmulatorAction = uninstallTask(true) dependsOn(packageDebug) describedAs("Uninstall package on the default emulator.")

  lazy val uninstallDevice = uninstallDeviceAction
  def uninstallDeviceAction = uninstallTask(false) dependsOn(packageDebug) describedAs("Uninstall package on the default device.")

  def installTask(emulator: Boolean) = defaultAdbTask(emulator, "install")
  def reinstallTask(emulator: Boolean) = defaultAdbTask(emulator, "install -r")
  def uninstallTask(emulator: Boolean) = defaultAdbTask(emulator, "uninstall")
  def defaultAdbTask(emulator: Boolean, action: String) = adbTask(adbPath, emulator, action, packageApkPath)
  def adbTask(adbPath: Path, emulator: Boolean, action: String, packageApkPath: Path) = execTask {<x>
      {adbPath.absolutePath} {if (emulator) "-e" else "-d"} {action} {packageApkPath.absolutePath}
   </x>}
   
  lazy val manifest:scala.xml.Elem = scala.xml.XML.loadFile(androidManifestPath.asFile)

  lazy val minSdkVersion = usesSdk("minSdkVersion")
  lazy val maxSdkVersion = usesSdk("maxSdkVersion")
  lazy val manifestPackage = manifest.attribute("package").getOrElse(error("package not defined")).text
    
  def usesSdk(s: String):Option[Int] = (manifest \ "uses-sdk").first.attribute("http://schemas.android.com/apk/res/android", s).map(_.text.toInt)

  def addonsJarPath = Path.lazyPathFinder {
    for {
      lib <- manifest \ "application" \ "uses-library"
      val p = lib.attribute("http://schemas.android.com/apk/res/android", "name").flatMap {
        _.text match {
          case "com.google.android.maps" => Some(mapsJarPath)
          case _ => None
        } 
      }   
      if p.isDefined
    } yield p.get
  }
  
  
  // these dependencies are already included in the Android SDK 
  // set the configuration to "provided" so they won't get included in the package
  val http_core = "org.apache.httpcomponents" % "httpcore" % "4.0.1" % "provided"
  val http_client = "org.apache.httpcomponents" % "httpclient" % "4.0" % "provided"
  val logging = "commons-logging" % "commons-logging"  % "1.1.1" % "provided"
  val codec = "commons-codec" % "commons-codec"  % "1.3" % "provided"

  override def ivyXML =
    <dependencies>
       <exclude module="httpclient" conf="compile"/>
       <exclude module="httpcore" conf="compile"/>              
       <exclude module="commons-logging" conf="compile"/>              
       <exclude module="commons-codec" conf="compile"/>      
    </dependencies>
}
