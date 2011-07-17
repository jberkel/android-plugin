import sbt._
import java.io.{ByteArrayOutputStream, File, PrintStream}

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
class ApkBuilder(project: Installable, debug: Boolean) {

  val classLoader = ClasspathUtilities.toLoader(project.androidToolsPath / "lib" / "sdklib.jar")
  val klass = classLoader.loadClass("com.android.sdklib.build.ApkBuilder")
  val constructor = klass.getConstructor(
    classOf[File], classOf[File], classOf[File], classOf[String], classOf[PrintStream])
  val keyStore = if (debug) getDebugKeystore else null
  val outputStream = new ByteArrayOutputStream
  val builder = constructor.newInstance(
    project.packageApkPath.asFile, project.resourcesApkPath.asFile, project.classesDexPath.asFile, keyStore, new PrintStream(outputStream))
  setDebugMode(debug)

  def build() = try {
    project.log.info("Packaging "+project.packageApkPath)
    addNativeLibraries(project.nativeLibrariesPath.asFile, null)
    addResourcesFromJar(project.classesMinJarPath.asFile)
    addSourceFolder(project.mainResourcesPath.asFile)
    sealApk
    None
  } catch {
    case e: Throwable => Some(e.getCause.getMessage)
  } finally {
    project.log.debug(outputStream.toString)
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
