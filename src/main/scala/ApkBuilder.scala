import sbt._
import classpath._
import java.io.{ByteArrayOutputStream, File, PrintStream}

// Replaces the Installable argument
case class ApkConfig(
  androidToolsPath: File, 
  packageApkPath: File,
  resourcesApkPath: File,
  classesDexPath: File,
  nativeLibrariesPath: File,
  classesMinJarPath: File,
  resourceDirectory: File
) 

/**
 * Build an APK - replaces the now-deprecated apkbuilder command-line executable.
 *
 * Google provides no supported means of building an APK from the command line.
 * Instead, we need to use the `ApkBuilder` class within `sdklib.jar`.
 *
 * The source for `ApkBuilder` is
 * [[http://android.git.kernel.org/?p=platform/sdk.git;a=blob;f=sdkmanager/libs/sdklib/src/com/android/sdklib/build/ApkBuilder.java here]].
 *
 * The source for Google's Ant task that uses it is
 * [[http://android.git.kernel.org/?p=platform/sdk.git;a=blob;f=anttasks/src/com/android/ant/ApkBuilderTask.java here]].
 */
class ApkBuilder(project: ApkConfig, debug: Boolean) {
  val classLoader = ClasspathUtilities.toLoader(project.androidToolsPath / "lib" / "sdklib.jar")
  val klass = classLoader.loadClass("com.android.sdklib.build.ApkBuilder")
  val constructor = klass.getConstructor(
    classOf[File], classOf[File], classOf[File], classOf[String], classOf[PrintStream])
  val keyStore = if (debug) getDebugKeystore else null
  val outputStream = new ByteArrayOutputStream
  val builder = constructor.newInstance(
    project.packageApkPath, project.resourcesApkPath, project.classesDexPath, keyStore, new PrintStream(outputStream))
  setDebugMode(debug)

  def build() = try {
    addNativeLibraries(project.nativeLibrariesPath, null)
    addResourcesFromJar(project.classesMinJarPath)
    addSourceFolder(project.resourceDirectory)
    sealApk
    Right("Packaging "+project.packageApkPath)
  } catch {
    case e: Throwable => Left(e.getCause.getMessage)
  }
  
  def getDebugKeystore = {
    val method = klass.getMethod("getDebugKeystore")
    method.invoke(null).asInstanceOf[String]
  }
  
  def setDebugMode(debug: Boolean) {
    val method = klass.getMethod("setDebugMode", classOf[Boolean])
    method.invoke(builder, debug.asInstanceOf[Object])
  }
  
  def addNativeLibraries(nativeFolder: File, abiFilter: String) {
    if (nativeFolder.exists && nativeFolder.isDirectory) {
      val method = klass.getMethod("addNativeLibraries", classOf[File], classOf[String])
      method.invoke(builder, nativeFolder, abiFilter)
    }
  } 

  /// Copy most non class files from the given standard java jar file
  ///
  /// (used to let classloader.getResource work for legacy java libs
  /// on android)
  def addResourcesFromJar(jarFile: File) {
    if (jarFile.exists) {
      def method = klass.getMethod("addResourcesFromJar", classOf[File])
      method.invoke(builder, jarFile)
    }
  }  

  def addSourceFolder(folder: File) {
    if (folder.exists) {
      def method = klass.getMethod("addSourceFolder", classOf[File])
      method.invoke(builder, folder)
    }
  }

  def sealApk() {
    val method = klass.getMethod("sealApk")
    method.invoke(builder)
  }
}
