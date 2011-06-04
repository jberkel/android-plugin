import java.io.File
import sbt._
import Process._

/** Provides some default values for the NdkSupport trait. */
object NdkSupport {
  /** The default name for the 'ndk-build' tool. */
  val DefaultNdkBuildName = "ndk-build"
  /** The default directory name for native sources. */
  val DefaultJniDirectoryName = "jni"
  /** The default directory name for compiled native objects. */
  val DefaultObjDirectoryName = "obj"
  /** The default directory name for compiled native libraries. */
  val DefaultLibsDirectoryName = "libs"
  /** The list of environment variables to check for the NDK. */
  val DefaultEnvs = List("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT")
}


/** Trait that mixes in compilation of C/C++ sources using the NDK.
  *
  * @author Daniel Solano Gómez */
trait NdkSupport extends BaseAndroidProject {
  import NdkSupport._

  // ndk-related names
  def ndkBuildName = DefaultNdkBuildName
  def jniDirectoryName = DefaultJniDirectoryName
  def objDirectoryName = DefaultObjDirectoryName
  def libsDirectoryName = DefaultLibsDirectoryName

  // ndk-related paths
  def mainJniSourcePath = mainSourcePath / jniDirectoryName
  def mainNativeOutputPath =
    Path.fromFile(mainJniSourcePath.asFile.getParentFile)
  def mainNativeObjectPath = mainNativeOutputPath / objDirectoryName
  def mainNativeLibraryPath = mainNativeOutputPath / libsDirectoryName
  def ndkBuildPath = androidNdkPath / ndkBuildName

  lazy val androidNdkPath =
    determineAndroidNdkPath.getOrElse(error("Android NDK not found.  "+
      "You might need to set "+DefaultEnvs.mkString(" or ")))

  /** Finds the NDK path by using some environment variables to see if they
    * point to a directory that contains the 'ndk-build' executable. */
  def determineAndroidNdkPath:Option[Path] = {
    val paths = for {
      e <- DefaultEnvs
      p = System.getenv(e)
      if p != null
      if new File(p, ndkBuildName).canExecute
    } yield p
    if (paths.isEmpty) None else Some(Path.fromFile(paths.first))
  }

  /** Make compile depend on ndkBuild. */
  override def compileAction = super.compileAction dependsOn ndkBuild
  /** Make clean action clean up 'obj' and 'libs' directories. */
  override def cleanAction =
    super.cleanAction dependsOn(cleanTask(mainNativeObjectPath),
                                cleanTask(mainNativeLibraryPath))

  // The new ndk-build action
  lazy val ndkBuild = ndkBuildAction
  def ndkBuildAction =
    ndkBuildTask describedAs("Compile native C/C++ sources.")
  /** Calls 'ndk-build' to compile native sources, changing to the parent
    * directory of the main JNI source path. */
  def ndkBuildTask = execTask {
      <x>
        {ndkBuildPath.absolutePath} -C {mainNativeOutputPath.absolutePath}
      </x>
    } dependsOn(directory(mainJniSourcePath))
}
