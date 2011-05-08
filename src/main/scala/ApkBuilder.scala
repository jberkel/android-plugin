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
    sealApk
    None
  } catch {
    case e: Throwable => Some(e.getCause.getMessage)
  } finally {
    project.log.info(outputStream.toString)
  }
  
  def getDebugKeystore = {
    val method = klass.getMethod("getDebugKeystore")
    method.invoke(null).asInstanceOf[String]
  }
  
  def setDebugMode(debug: Boolean) {
    val method = klass.getMethod("setDebugMode", classOf[Boolean])
    method.invoke(builder, debug.asInstanceOf[Object])
  }
  
  def sealApk() {
    val method = klass.getMethod("sealApk")
    method.invoke(builder)
  }
}
