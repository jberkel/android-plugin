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
  /** The make environment variable name for the javah generated header directory. */
  val DefaultJavahOutputEnv = "SBT_MANAGED_JNI_INCLUDE"

  lazy val defaultSettings: Seq[Setting[_]] = inConfig(Android) (Seq (
    ndkBuildName := DefaultNdkBuildName,
    jniDirectoryName := DefaultJniDirectoryName,
    objDirectoryName := DefaultObjDirectoryName,
    ndkEnvs := DefaultEnvs,
    javahName := "javah",
    javahOutputEnv := DefaultJavahOutputEnv
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
      },

    javahPath <<= (javaHome, javahName) apply { (home, name) =>
      home map ( h => (h / "bin" / name).absolutePath ) getOrElse name
    },

    javahOutputDirectory <<= (sourceManaged)(_ / "main" / DefaultJniDirectoryName )

  ))

  private def javahTask(
    javahPath: String,
    classpath: Seq[File],
    classes: Seq[String],
    outputDirectory: File,
    streams: TaskStreams) {

    val log = streams.log
    if (classes.isEmpty) {
      log.debug("No JNI classes, skipping javah")
    } else {
      val classpathArgument = classpath.map(_.getAbsolutePath()).mkString(File.pathSeparator)
      val javahCommandLine = Seq(
        javahPath,
        "-classpath", classpathArgument,
        "-d", outputDirectory.absolutePath) ++ classes
      log.debug("Running javah: " + (javahCommandLine mkString " "))
      val p = Process(javahCommandLine)
      val exitCode = p ! log

      if (exitCode != 0) {
        sys.error("javah exited with " + exitCode)
      }
    }
  }

  private def ndkBuildTask(targets: String*) =
    (ndkBuildPath, javahOutputEnv, javahOutputDirectory, nativeOutputPath) map {
    (ndkBuildPath, javahOutputEnv, javahOutputDirectory, obj) =>
      val exitValue = Process(ndkBuildPath.absolutePath :: "-C" :: obj.absolutePath ::
          (javahOutputEnv + "=" + javahOutputDirectory.absolutePath) ::
          targets.toList) !

      if(exitValue != 0) sys.error("ndk-build failed with nonzero exit code (" + exitValue + ")")

      ()
    }

  lazy val settings: Seq[Setting[_]] = defaultSettings ++ pathSettings ++ inConfig(Android) (Seq (
    javah <<= (
        (compile in Compile),
        javahPath,
        (classDirectory in Compile), (internalDependencyClasspath in Compile), (externalDependencyClasspath in Compile),
        jniClasses,
        javahOutputDirectory,
        streams) map ((
            _, // we only depend on a side effect (built classes) of compile
            javahPath,
            classDirectory, internalDependencyClasspath, externalDependencyClasspath,
            jniClasses,
            javahOutputDirectory,
            streams) =>
        javahTask(
          javahPath,
          Seq(classDirectory) ++ internalDependencyClasspath.files ++ externalDependencyClasspath.files,
          jniClasses,
          javahOutputDirectory,
          streams)
    	),
    ndkBuild <<= ndkBuildTask(),
    ndkBuild <<= ndkBuild.dependsOn(javah),
    ndkClean <<= ndkBuildTask("clean"),
    jniClasses := Seq.empty,
    (products in Compile) <<= (products in Compile).dependsOn(ndkBuild),
    javahClean <<= (javahOutputDirectory) map IO.delete
  )) ++ Seq (
    cleanFiles <+= (nativeObjectPath in Android),
    clean <<= clean.dependsOn(ndkClean in Android, javahClean in Android)
  )
}
