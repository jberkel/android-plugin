import proguard.{Configuration=>ProGuardConfiguration, ProGuard, ConfigurationParser}
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
  val DefaultAndroidManifestName = "AndroidManifest.xml"
  val DefaultAndroidJarName = "android.jar"
  val DefaultMapsJarName = "maps.jar"  
  val DefaultAssetsDirectoryName = "assets"
  val DefaultResDirectoryName = "res"
  val DefaultClassesMinJarName = "classes.min.jar"
  val DefaultClassesDexName = "classes.dex"
  val DefaultResourcesApkName = "resources.apk"
  val DefaultDxJavaOpts = "-JXmx512m"
}

abstract class AndroidProject(info: ProjectInfo) extends DefaultProject(info) {
  def proguardOption = ""
  def proguardInJars = runClasspath --- proguardExclude
  def proguardExclude = libraryJarPath +++ mainCompilePath +++ mainResourcesPath +++ managedClasspath(Configurations.Provided)
  def libraryJarPath = androidJarPath +++ addonsJarPath
  override def unmanagedClasspath = super.unmanagedClasspath +++ libraryJarPath
  
  import AndroidProject._
  
  def androidPlatformName:String
   
  def aaptName = DefaultAaptName // note: this is a .exe file in windows
  def adbName = DefaultAdbName
  def aidlName = DefaultAidlName
  def apkbuilderName = DefaultApkbuilderName + osBatchSuffix
  def dxName = DefaultDxName + osBatchSuffix
  def androidManifestName = DefaultAndroidManifestName
  def androidJarName = DefaultAndroidJarName
  def mapsJarName = DefaultMapsJarName
  def assetsDirectoryName = DefaultAssetsDirectoryName
  def resDirectoryName = DefaultResDirectoryName
  def classesMinJarName = DefaultClassesMinJarName
  def classesDexName = DefaultClassesDexName
  def packageApkName = artifactBaseName + ".apk"
  def resourcesApkName = DefaultResourcesApkName
  def dxJavaOpts = DefaultDxJavaOpts

  def scalaHomePath  = Path.fromFile(new File(System.getProperty("scala.home")))
  def androidSdkPath = {
    val sdk = System.getenv("ANDROID_SDK_HOME")
    if (sdk == null) error("You need to set ANDROID_SDK_HOME")
    Path.fromFile(new File(sdk))
  }
  def apiLevel = minSdkVersion.getOrElse(platformName2ApiLevel)
  def isWindows = System.getProperty("os.name").startsWith("Windows")
  def osBatchSuffix = if (isWindows) ".bat" else ""
  
  def dxMemoryParameter = {
    // per http://code.google.com/p/android/issues/detail?id=4217, dx.bat
    // doesn't currently support -JXmx arguments.  For now, omit them in windows.
    if (isWindows) "" else dxJavaOpts
  }
  def platformName2ApiLevel:Int = androidPlatformName match {
    case "android-1.0" => 1
    case "android-1.1" => 2
    case "android-1.5" => 3
    case "android-1.6" => 4
    case "android-2.0" => 5
    case "android-2.1" => 7
  }
  
  def androidToolsPath = androidSdkPath / "tools"
  def apkbuilderPath = androidToolsPath / apkbuilderName
  def adbPath = androidToolsPath / adbName
  def androidPlatformPath = androidSdkPath / "platforms" / androidPlatformName
  def platformToolsPath = androidPlatformPath / "tools"
  def aaptPath = platformToolsPath / aaptName
  def aidlPath = platformToolsPath / aidlName
  def dxPath = platformToolsPath / dxName

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
    </x>} dependsOn directory(mainJavaSourcePath)

  lazy val aidl = aidlAction
  def aidlAction = aidlTask describedAs("Generate Java classes from .aidl files.")
  def aidlTask = execTask {
  	val aidlPaths = descendents(mainSourceRoots, "*.aidl").getPaths
  	if(aidlPaths.isEmpty)
  		Process(true)
  	else 
          aidlPaths.toList.map {ap =>
            aidlPath.absolutePath :: "-o" + mainJavaSourcePath.absolutePath :: "-I" + mainJavaSourcePath.absolutePath :: ap :: Nil}.foldLeft(None.asInstanceOf[Option[ProcessBuilder]]){(f, s) => f match{
              case None => Some(s)
              case Some(first) => Some(first ## s)
            }
          }.get
  }
  
  override def compileAction = super.compileAction dependsOn(aaptGenerate, aidl)
  
  /** Forward compatibility with sbt 0.6+ Scala build versions */
  def scalaLibraryJar = try {
    type xsbtProject = { def buildScalaInstance: { def libraryJar: File } }
    this.asInstanceOf[xsbtProject].buildScalaInstance.libraryJar
  } catch {
    case e: NoSuchMethodException => FileUtilities.scalaLibraryJar
  }
  lazy val proguard = proguardAction
  def proguardAction = proguardTask dependsOn(compile) describedAs("Optimize class files.")
  def proguardTask = task { 
    val args = "-injars" ::  mainCompilePath.absolutePath+File.pathSeparator+
                             scalaLibraryJar.getAbsolutePath+"(!META-INF/MANIFEST.MF,!library.properties)"+
                             (if (!proguardInJars.getPaths.isEmpty) File.pathSeparator+proguardInJars.getPaths.map(_+"(!META-INF/MANIFEST.MF)").mkString(File.pathSeparator) else "") ::                             
               "-outjars" :: classesMinJarPath.absolutePath ::
               "-libraryjars" :: libraryJarPath.getPaths.mkString(File.pathSeparator) :: 
               "-dontwarn" :: "-dontoptimize" :: "-dontobfuscate" :: 
               "-keep public class * extends android.app.Activity" ::
               "-keep public class * extends android.app.Service" ::
               "-keep public class * extends android.appwidget.AppWidgetProvider" ::
               "-keep public class * extends android.content.BroadcastReceiver" ::
               "-keep public class * implements junit.framework.Test { public void test*(); }" :: proguardOption :: Nil
    
    val config = new ProGuardConfiguration
    new ConfigurationParser(args.toArray[String], info.projectPath.asFile).parse(config)    
    new ProGuard(config).execute
    None
  }

  lazy val dx = dxAction
  def dxAction = dxTask dependsOn(proguard) describedAs("Convert class files to dex files")
  def dxTask = fileTask(classesDexPath from classesMinJarPath) { 
     execTask {<x> {dxPath.absolutePath} {dxMemoryParameter} 
        --dex --output={classesDexPath.absolutePath} {classesMinJarPath.absolutePath}
    </x> } run } 
  
  lazy val aaptPackage = aaptPackageAction
  def aaptPackageAction = aaptPackageTask dependsOn(dx) describedAs("Package resources and assets.")
  def aaptPackageTask = execTask {<x>
    {aaptPath.absolutePath} package -f -M {androidManifestPath.absolutePath} -S {mainResPath.absolutePath} 
       -A {mainAssetsPath.absolutePath} -I {androidJarPath.absolutePath} -F {resourcesApkPath.absolutePath}
  </x>} dependsOn directory(mainAssetsPath)

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
  def uninstallEmulatorAction = uninstallTask(true) describedAs("Uninstall package on the default emulator.")

  lazy val uninstallDevice = uninstallDeviceAction
  def uninstallDeviceAction = uninstallTask(false) describedAs("Uninstall package on the default device.")

  def installTask(emulator: Boolean) = adbTask(emulator, "install "+packageApkPath.absolutePath)
  def reinstallTask(emulator: Boolean) = adbTask(emulator, "install -r "+packageApkPath.absolutePath)
  def uninstallTask(emulator: Boolean) = adbTask(emulator, "uninstall "+manifestPackage)
  
  def adbTask(emulator: Boolean, action: String) = execTask {<x>
      {adbPath.absolutePath} {if (emulator) "-e" else "-d"} {action}
   </x>}
         
  lazy val manifest:scala.xml.Elem = scala.xml.XML.loadFile(androidManifestPath.asFile)

  lazy val minSdkVersion = usesSdk("minSdkVersion")
  lazy val maxSdkVersion = usesSdk("maxSdkVersion")
  lazy val manifestPackage = manifest.attribute("package").getOrElse(error("package not defined")).text
    
  def usesSdk(s: String):Option[Int] = (manifest \ "uses-sdk").first.attribute("http://schemas.android.com/apk/res/android", s).map(_.text.toInt)

  def addonsJarPath = Path.lazyPathFinder {
    for {
      lib <- manifest \ "application" \ "uses-library"
      p = lib.attribute("http://schemas.android.com/apk/res/android", "name").flatMap {
        _.text match {
          case "com.google.android.maps" => Some(mapsJarPath)
          case _ => None
        } 
      }   
      if p.isDefined
    } yield p.get
  }
  
  def directory(dir: Path) = fileTask(dir :: Nil) {
    FileUtilities.createDirectory(dir, log)
  }
  
  override def ivyXML =
    <dependencies>
       <exclude module="httpclient" conf="compile"/>
       <exclude module="httpcore" conf="compile"/>              
       <exclude module="commons-logging" conf="compile"/>              
       <exclude module="commons-codec" conf="compile"/>      
       <exclude module="scala-library" conf="compile"/>                    
    </dependencies>
}
