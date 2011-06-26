//import proguard.{Configuration=>ProGuardConfiguration, ProGuard, ConfigurationParser}
import sbt._

import Process._
import Keys._

object BaseAndroidProject extends Plugin {
  val Android = config("Android")

  /*
  lazy val androidSdkPath = determineAndroidSdkPath.getOrElse(error("Android SDK not found."+
            "You might need to set "+DefaultEnvs.mkString(" or ")))

  def determineAndroidSdkPath:Option[Path] = {
    val paths = for { e <- DefaultEnvs ; p = System.getenv(e); if p != null } yield p
    if (paths.isEmpty) None else Some(Path.fromFile(paths.first))
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
    case "android-2.2" => 8
    case "android-2.3" => 9
    case "android-2.3.3" => 10
    case "android-3.0" => 11
  }

  def androidToolsPath = androidSdkPath / "tools"
  def adbPath = platformToolsPath / adbName
  def androidPlatformPath = androidSdkPath / "platforms" / androidPlatformName
  def platformToolsPath = androidSdkPath / "platform-tools"
  def aaptPath = platformToolsPath / aaptName
  def aidlPath = platformToolsPath / aidlName
  def dxPath = platformToolsPath / dxName

  def androidManifestPath =  mainSourcePath / androidManifestName
  def androidJarPath = androidPlatformPath / androidJarName
  def nativeLibrariesPath = mainSourcePath / "libs"
  def addonsPath = androidSdkPath / "add-ons" / ("addon_google_apis_google_inc_" + apiLevel) / "libs"
  def mapsJarPath = addonsPath / DefaultMapsJarName
  def mainAssetsPath = mainSourcePath / assetsDirectoryName
  def mainResPath = mainSourcePath / resDirectoryName
  def managedJavaPath = "src_managed" / "main" / "java"
  def classesMinJarPath = outputPath / classesMinJarName
  def classesDexPath =  outputPath / classesDexName
  def resourcesApkPath = outputPath / resourcesApkName
  def packageApkPath = outputPath / packageApkName
  def skipProguard = false

  override def mainSourceRoots = super.mainSourceRoots +++ (managedJavaPath##)
  override def cleanAction = super.cleanAction dependsOn cleanTask(managedJavaPath)

  lazy val aaptGenerate = aaptGenerateAction
  def aaptGenerateAction = aaptGenerateTask describedAs("Generate R.java.")

  def aaptGenerateTask = execTask { 
    aaptGenerateCommands reduceLeft (_ #&& _)
  } dependsOn(directory(managedJavaPath))

  def aaptGenerateCommands = androidProjects map (project => aaptGenerateCommand(project))

  def aaptGenerateCommand(project: BaseAndroidProject) = Process(<x>
      {aaptPath.absolutePath} package --auto-add-overlay -m
        --custom-package {project.manifestPackage}
        -M {androidManifestPath.absolutePath}
        -S {resPaths.getPaths.mkString(" -S ")}
        -I {androidJarPath.absolutePath}
        -J {managedJavaPath.absolutePath}
    </x>)
    
  def androidProjects = projectClosure.filter(_.isInstanceOf[BaseAndroidProject]).map(_.asInstanceOf[BaseAndroidProject]).reverse
    
  def resPaths = (Path.emptyPathFinder /: androidProjects.map(_.mainResPath))(_ +++ _)

  lazy val aidl = aidlAction
  def aidlAction = aidlTask describedAs("Generate Java classes from .aidl files.")
  def aidlTask = execTask {
    val aidlPaths = descendents(mainSourceRoots, "*.aidl").getPaths
    if(aidlPaths.isEmpty)
      Process(true)
    else
       aidlPaths.toList.map { ap =>
            aidlPath.absolutePath ::
               "-o" + managedJavaPath.absolutePath ::
               "-I" + mainJavaSourcePath.absolutePath ::
                ap :: Nil }.foldLeft(None.asInstanceOf[Option[ProcessBuilder]]) { (f, s) =>
          f match {
              case None        => Some(s)
              case Some(first) => Some(first #&& s)
          }
        }.get
  }

  override def compileAction = super.compileAction dependsOn(aaptGenerate, aidl)

  def adbTask(emulator: Boolean, action: => String) = execTask {<x>
      {adbPath.absolutePath} {if (emulator) "-e" else "-d"} {action}
   </x>}

  lazy val manifest:scala.xml.Elem = scala.xml.XML.loadFile(androidManifestPath.asFile)

  lazy val minSdkVersion = usesSdk("minSdkVersion")
  lazy val maxSdkVersion = usesSdk("maxSdkVersion")
  lazy val manifestPackage = manifest.attribute("package").getOrElse(error("package not defined")).text

  def usesSdk(s: String):Option[Int] = (manifest \ "uses-sdk").first.attribute(manifestSchema, s).map(_.text.toInt)

  def addonsJarPath = Path.lazyPathFinder {
    for {
      lib <- manifest \ "application" \ "uses-library"
      p = lib.attribute(manifestSchema, "name").flatMap {
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
  */

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

  /** Tasks */
  val aaptGenerate = TaskKey[Seq[File]]("apt-generate")

  /** Commands */
  
  override val settings = inConfig(Android) (Seq (
    // Default Settings
    aptName := "aapt",
    dbName := "adb",
    idlName := "aidl",
    dxName := "dx",
    manifestName := "AndroidManifest.xml",
    jarName := "android.jar",
    mapsJarName := "maps.jar",
    assetsDirectoryName := "assests",
    resDirectoryName := "res",
    classesMinJarName := "classes.min.jar",
    classesDexName := "classes.dex",
    resourcesApkName := "resources.apk", 
    dxJavaOpts := "-JXmx512m",
    manifestSchema := "http://schemas.android.com/apk/res/android",
    envs := Seq("ANDROID_SDK_HOME", "ANDROID_SDK_ROOT", "ANDROID_HOME")
  ))
}

/*
abstract class BaseAndroidProject(info: ProjectInfo) extends DefaultProject(info) {
  def proguardOption = ""
  def proguardInJars = runClasspath --- proguardExclude
  def proguardExclude = libraryJarPath +++ mainCompilePath +++ mainResourcesPath +++ managedClasspath(Configurations.Provided)
  def libraryJarPath = androidJarPath +++ addonsJarPath
  override def unmanagedClasspath = super.unmanagedClasspath +++ libraryJarPath

  import BaseAndroidProject._

  def androidPlatformName:String

  def aaptName = DefaultAaptName // note: this is a .exe file in windows
  def adbName = DefaultAdbName
  def aidlName = DefaultAidlName
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
  def manifestSchema = DefaultManifestSchema


  override def ivyXML =
    <dependencies>
       <exclude module="httpclient" conf="compile"/>
       <exclude module="httpcore" conf="compile"/>
       <exclude module="commons-logging" conf="compile"/>
       <exclude module="commons-codec" conf="compile"/>
       <exclude module="scala-library" conf="compile"/>
    </dependencies>
}
*/
