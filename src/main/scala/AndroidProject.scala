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
  val DefaultAssetsDirectoryName = "assets"
  val DefaultResDirectoryName = "res"
  val DefaultClassesMinJarName = "classes.min.jar"
  val DefaultClassesDexName = "classes.dex"
  val DefaultResourcesApkName = "resources.apk"
}

abstract class AndroidProject(info: ProjectInfo) extends DefaultProject(info) {

  def proguardOption = ""
  def proguardInJars = runClasspath --- proguardExclude
  def proguardExclude = (androidJarPath +++ mainCompilePath +++ mainResourcesPath)
  override def unmanagedClasspath = super.unmanagedClasspath +++ androidJarPath

  import AndroidProject._

  def aaptName = DefaultAaptName
  def adbName = DefaultAdbName
  def aidlName = DefaultAidlName
  def apkbuilderName = DefaultApkbuilderName
  def dxName = DefaultDxName
  def androidPlatformName = DefaultAndroidPlatformName
  def androidManifestName = DefaultAndroidManifestName
  def androidJarName = DefaultAndroidJarName
  def assetsDirectoryName = DefaultAssetsDirectoryName
  def resDirectoryName = DefaultResDirectoryName
  def classesMinJarName = DefaultClassesMinJarName
  def classesDexName = DefaultClassesDexName
  def packageApkName = artifactBaseName + ".apk"
  def resourcesApkName = DefaultResourcesApkName

  def scalaHomePath = Path.fromFile(new File(System.getProperty("scala.home")))
  def androidSdkPath: Path
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
      -libraryjars {androidJarPath.absolutePath}
      -dontwarn
      -dontoptimize
      -dontobfuscate
      -keep public class * extends android.app.Activity
      -keep public class * extends android.app.Service
      -keep public class * extends android.appwidget.AppWidgetProvider
      {proguardOption}
      </a>.text)
    None
  }
  
  lazy val dx = dxAction
  def dxAction = dxTask dependsOn(proguard) describedAs("Convert class files to dex files")
  def dxTask = execTask {<x> {dxPath.absolutePath} --dex --output={classesDexPath.absolutePath} {classesMinJarPath.absolutePath}</x> }

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
}
