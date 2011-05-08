import proguard.{Configuration=>ProGuardConfiguration, ProGuard, ConfigurationParser}
import java.io._
import sbt._
import Process._

trait Installable extends BaseAndroidProject {

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

  lazy val aaptPackage = aaptPackageAction
  def aaptPackageAction = aaptPackageTask dependsOn(dx) describedAs("Package resources and assets.")
  def aaptPackageTask = execTask {<x>
    {aaptPath.absolutePath} package --auto-add-overlay -f
      -M {androidManifestPath.absolutePath}
      -S {resPaths.getPaths.mkString(" -S ")}
      -A {mainAssetsPath.absolutePath}
      -I {androidJarPath.absolutePath}
      -F {resourcesApkPath.absolutePath}
  </x>} dependsOn directory(mainAssetsPath)

  lazy val packageDebug = packageDebugAction
  def packageDebugAction = packageTask(true) dependsOn(aaptPackage) describedAs("Package and sign with a debug key.")

  lazy val packageRelease = packageReleaseAction
  def packageReleaseAction = packageTask(false) dependsOn(aaptPackage) describedAs("Package without signing.")

  lazy val cleanApk = cleanTask(packageApkPath) describedAs("Remove apk package")
  def packageTask(signPackage: Boolean) = task {new ApkBuilder(this, signPackage).build} dependsOn(cleanApk)

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
                           (if (!proguardInJars.getPaths.isEmpty)
                            File.pathSeparator+proguardInJars.getPaths.map(_+"(!META-INF/MANIFEST.MF,!**/R.class,!**/R$*.class,!**/TR.class,!**/TR$*.class)").mkString(File.pathSeparator) else "") ::
             "-outjars" :: classesMinJarPath.absolutePath ::
             "-libraryjars" :: libraryJarPath.getPaths.mkString(File.pathSeparator) ::
             "-dontwarn" :: "-dontoptimize" :: "-dontobfuscate" ::
             "-dontnote scala.Enumeration" ::
             "-dontnote org.xml.sax.EntityResolver" ::
             "-keep public class * extends android.app.Activity" ::
             "-keep public class * extends android.app.Service" ::
             "-keep public class * extends android.appwidget.AppWidgetProvider" ::
             "-keep public class * extends android.content.BroadcastReceiver" ::
             "-keep public class * extends android.content.ContentProvider" ::
             "-keep public class * extends android.view.View" ::
             "-keep public class * extends android.app.Application" ::
             "-keep public class "+manifestPackage+".** { public protected *; }" ::
             "-keep public class * implements junit.framework.Test { public void test*(); }" :: proguardOption :: Nil
    val config = new ProGuardConfiguration
    new ConfigurationParser(args.toArray[String], info.projectPath.asFile).parse(config)
    new ProGuard(config).execute
    None
  }

  def dxProguardAction = dxProguardTask dependsOn(proguard) describedAs("Convert class files to dex files")
  def dxProguardTask = fileTask(classesDexPath from classesMinJarPath) {
     execTask {
      <x> {dxPath.absolutePath} {dxMemoryParameter}
        --dex --output={classesDexPath.absolutePath} {classesMinJarPath.absolutePath}
      </x>
     } run
  }

  def dxAction = dxTask dependsOn(compile) describedAs("Convert class files to dex files")
  def dxTask = fileTask(classesDexPath from descendents(mainCompilePath, "*") +++ proguardInJars) {
     execTask {
      <x> {dxPath.absolutePath} {dxMemoryParameter}
        --dex --output={classesDexPath.absolutePath}
        {mainCompilePath.absolutePath} {proguardInJars.getPaths.mkString(" ")}
      </x>
     } run
  }
  lazy val dx = if (!skipProguard) dxProguardAction else dxAction
}
