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
  managedNativePath: File,
  resourceContainingJars: Seq[File],
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
  type JApkBuilder = Object
  // sdklib has not been packaged yet, need to load dynamically
  val klass = ClasspathUtilities.toLoader(project.androidToolsPath / "lib" / "sdklib.jar")
                                .loadClass("com.android.sdklib.build.ApkBuilder")
  val outputStream = new ByteArrayOutputStream

  def build():Either[String, String] = try {
    val constructor = klass.getConstructor(
      classOf[File], classOf[File], classOf[File], classOf[String], classOf[PrintStream])
    val builder:JApkBuilder = constructor.newInstance(
        project.packageApkPath,
        project.resourcesApkPath,
        project.classesDexPath,
        if (debug) getDebugKeystore else null,
        new PrintStream(outputStream)
    ).asInstanceOf[JApkBuilder]

    setDebugMode(builder, debug)
    addNativeLibraries(builder, project.nativeLibrariesPath, null)
    addNativeLibraries(builder, project.managedNativePath, null)
    for (file <- project.resourceContainingJars; if file.isFile)
      addResourcesFromJar(builder, file)
    addSourceFolder(builder, project.resourceDirectory)
    sealApk(builder)

    Right("Packaging "+project.packageApkPath)
  } catch {
    case e: Throwable => Left(
        String.format("\n%s\nError packaging %s: %s",
          outputStream.toString,
          project.packageApkPath,
          if (e.getCause != null) e.getCause.getMessage else e.getMessage))
  }

  def getDebugKeystore = klass.getMethod("getDebugKeystore").invoke(null).asInstanceOf[String]

  def setDebugMode(builder: JApkBuilder, debug: Boolean) {
    klass.getMethod("setDebugMode", classOf[Boolean])
         .invoke(builder, debug.asInstanceOf[Object])
  }

  def addNativeLibraries(builder: JApkBuilder, nativeFolder: File, abiFilter: String) {
    if (nativeFolder.exists && nativeFolder.isDirectory) {
      try {
        klass.getMethod("addNativeLibraries", classOf[File], classOf[String])
             .invoke(builder, nativeFolder, abiFilter)
      } catch {
        case e: java.lang.NoSuchMethodException => {
          klass.getMethod("addNativeLibraries", classOf[File])
               .invoke(builder, nativeFolder)
        }
      }
    }
  }

  /// Copy most non class files from the given standard java jar file
  ///
  /// (used to let classloader.getResource work for legacy java libs
  /// on android)
  def addResourcesFromJar(builder: JApkBuilder, jarFile: File) {
    if (jarFile.isFile) {
      def method = klass.getMethod("addResourcesFromJar", classOf[File])
      method.invoke(builder, jarFile)
    }
  }

  def addSourceFolder(builder: JApkBuilder, folder: File) {
    if (folder.exists) {
      klass.getMethod("addSourceFolder", classOf[File])
           .invoke(builder, folder)
    }
  }

  def sealApk(builder: JApkBuilder) { klass.getMethod("sealApk").invoke(builder) }
}
