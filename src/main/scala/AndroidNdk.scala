import sbt._

import Keys._
import AndroidKeys.Android
import AndroidNdkKeys._

import java.io.File

/**
 * Support for the Android NDK.
 *
 * Adding support for compilation of C/C++ sources using the NDK.
 *
 * Adapted from work by Daniel Solano Gómez
 *
 * @author Daniel Solano Gómez, Martin Kneissl.
 */
object AndroidNdk {
  /** The default name for the 'ndk-build' tool. */
  val DefaultNdkBuildName = "ndk-build"
  /** The default directory name for native sources. */
  val DefaultJniDirectoryName = "jni"
  /** The default directory name for compiled native objects. */
  val DefaultObjDirectoryName = "obj"
  /** The list of environment variables to check for the NDK. */
  val DefaultEnvs = List("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT")


  lazy val defaultSettings: Seq[Setting[_]] = inConfig(Android) (Seq (
    ndkBuildName := DefaultNdkBuildName,
    jniDirectoryName := DefaultJniDirectoryName,
    objDirectoryName := DefaultObjDirectoryName,
    ndkEnvs := DefaultEnvs
    ))

  // ndk-related paths
  lazy val pathSettings: Seq[Setting[_]] = inConfig(Android) (Seq (
    jniSourcePath <<= (sourceDirectory, jniDirectoryName) (_ / _),
    nativeOutputPath <<= (jniSourcePath) (_.getParentFile),
    nativeObjectPath <<= (nativeOutputPath, objDirectoryName) (_ / _),
    ndkBuildPath <<= (ndkEnvs, ndkBuildName) { (envs, ndkBuildName) =>
      val paths = for {
	    e <- envs
	    p = System.getenv(e)
	    if p != null
	    b = new File(p, ndkBuildName)
	    if b.canExecute
	  } yield b
	  paths.headOption getOrElse (sys.error("Android NDK not found.  " +
        "You might need to set " + envs.mkString(" or ")))
      }
  ))


  private def ndkBuildTask(targets: String*) =
    (ndkBuildPath, nativeOutputPath) map { (ndkBuildPath, obj) =>
      val exitValue = Process(ndkBuildPath.absolutePath :: "-C" :: obj.absolutePath :: targets.toList) !

      if(exitValue != 0) sys.error("ndk-build failed with nonzero exit code (" + exitValue + ")")

      ()
    }

  lazy val settings: Seq[Setting[_]] = defaultSettings ++ pathSettings ++ inConfig(Android) (Seq (
    ndkBuild <<= ndkBuildTask(),
    ndkClean <<= ndkBuildTask("clean"),
    (compile in Compile) <<= (ndkBuild in Android, compile in Compile) map { (ndkBuild, compile) => ndkBuild ; compile }
  )) ++ Seq (
    cleanFiles <+= (nativeObjectPath in Android),
    clean <<= (clean, ndkClean in Android) map { (clean, ndkClean) => ndkClean ; clean  }
  )
}
