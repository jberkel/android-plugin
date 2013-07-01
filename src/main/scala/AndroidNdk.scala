package sbtandroid

import sbt._

import Keys._
import AndroidPlugin._

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
  /** The default directory name for compiled libraries. */
  val DefaultLibDirectoryName = "lib"
  /** The list of environment variables to check for the NDK. */
  val DefaultEnvs = List("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT")
  /** The make environment variable name for the javah generated header directory. */
  val DefaultJavahOutputEnv = "SBT_MANAGED_JNI_INCLUDE"
  /** The make environment variable name for the native libraries in lib/ */
  val DefaultNdkUnmanagedEnv = "SBT_UNMANAGED_NATIVE"

  /**
   * Default NDK settings
   */
  lazy val globalSettings: Seq[Setting[_]] = (Seq (

    // Options for ndk-build
    ndkBuildName := DefaultNdkBuildName,
    ndkJniDirectoryName := DefaultJniDirectoryName,
    ndkObjDirectoryName := DefaultObjDirectoryName,
    ndkLibDirectoryName := DefaultLibDirectoryName,
    ndkUnmanagedEnv := DefaultNdkUnmanagedEnv,
    ndkEnvs := DefaultEnvs,

    // Options for javah
    javahName := "javah",
    javahOutputEnv := DefaultJavahOutputEnv,
    javahOutputFile := None,

    // Path to the ndk-build executable
    ndkBuildPath <<= (ndkEnvs, ndkBuildName) { (envs, ndkBuildName) =>
      val paths = for {
        e <- envs
        p = System.getenv(e)
        if p != null
        b = new File(p, ndkBuildName)
        if b.canExecute
      } yield b
      paths.headOption
    },

    // Path to the javah executable
    javahPath <<= (javaHome, javahName) apply { (home, name) =>
      home map ( h => (h / "bin" / name).absolutePath ) getOrElse name
    },

    // List of classes against which we run javah
    jniClasses := Seq.empty
  ))

  /**
   * NDK-related paths
   */
  lazy val pathSettings: Seq[Setting[_]] = (Seq (

    // Path to the JNI sources
    ndkJniSourcePath <<= (sourceDirectory, ndkJniDirectoryName) (_ / _),

    // Path to the output .so libraries
    ndkNativeOutputPath <<= (crossTarget, ndkLibDirectoryName, configuration) (_ / _ / _.name),

    // Path to the compiled object files
    ndkNativeObjectPath <<= (crossTarget, ndkObjDirectoryName, configuration) (_ / _ / _.name),

    // Path to the javah include directory
    javahOutputDirectory <<= (sourceManaged, ndkJniDirectoryName) (_ / _)

  ))

  private def split(file: File) = {
    val parentsBottomToTop = Iterator.iterate(file)(_.getParentFile).takeWhile(_ != null).map(_.getName).toSeq
    parentsBottomToTop.reverse
  }

  private def compose(parent: File, child: File): File = {
    if (child.isAbsolute) {
      child
    } else {
      split(child).foldLeft(parent)(new File(_,_))
    }
  }

  private def javahTask(
    javahPath: String,
    classpath: Seq[File],
    classes: Seq[String],
    outputDirectory: File,
    outputFile: Option[File],
    streams: TaskStreams) {

    val log = streams.log
    if (classes.isEmpty) {
      log.debug("No JNI classes, skipping javah")

    } else {
      outputDirectory.mkdirs()

      val classpathArgument = classpath.map(_.getAbsolutePath()).mkString(File.pathSeparator)
      val outputArguments = outputFile match {
        case Some(file) =>
          val outputFile = compose(outputDirectory, file)
          // Neither javah nor RichFile.relativeTo will work unless the directories exist.
          Option(outputFile.getParentFile) foreach (_.mkdirs())
          if (! (outputFile relativeTo outputDirectory).isDefined) {
            log.warn("javah output file [" + outputFile + "] is not within javah output directory [" +
                outputDirectory + "], continuing anyway")
          }

          Seq("-o", outputFile.absolutePath)
        case None => Seq("-d", outputDirectory.absolutePath)
      }

      val javahCommandLine = Seq(
        javahPath,
        "-classpath", classpathArgument) ++
        outputArguments ++ classes

      log.debug("Running javah: " + (javahCommandLine mkString " "))
      val exitCode = Process(javahCommandLine) ! log

      if (exitCode != 0) {
        sys.error("javah exited with " + exitCode)
      }
    }
  }

  private def runNdkBuild(
    ndkBuildPath: Option[File],
    ndkUnmanagedEnv: String,
    javahOutputEnv: String,
    javahOutputDirectory: File,
    ndkNativeObjectPath: File,
    ndkNativeOutputPath: File,
    ndkJniSourcePath: File,
    envs: Seq[String],
    streams: TaskStreams,
    targets: String*): Seq[File] = {

    if (ndkJniSourcePath.exists) {

      // Arch-dependant output directory
      val unmanagedArch = ndkNativeOutputPath / "${TARGET_ARCH_ABI}"

      // Source root for ndk-build
      val sourceBase = ndkJniSourcePath.getParentFile

      // NDK-Build path
      val ndkBuildTool = ndkBuildPath getOrElse (sys.error("Android NDK not found.  " +
        "You might need to set " + envs.mkString(" or ")))

      // Create the ndk-build command
      val ndkBuild = (
        ndkBuildTool.absolutePath ::
        "-C" :: sourceBase.absolutePath ::
        (javahOutputEnv + "=" + javahOutputDirectory.absolutePath) ::
        (ndkUnmanagedEnv + "=" + unmanagedArch.absolutePath) ::
        ("NDK_APP_OUT=" + ndkNativeObjectPath.absolutePath) ::
        ("NDK_APP_DST_DIR=" + unmanagedArch.absolutePath) ::
        targets.toList
      )

      // Run that command
      streams.log.debug("Running ndk-build: " + ndkBuild.mkString(" "))
      val exitValue = ndkBuild.run(false).exitValue
      if (exitValue != 0) sys.error("ndk-build failed with nonzero exit code (" + exitValue + ")")

      // Return the output path
      Seq(ndkNativeOutputPath)

    // Return nothing if there is no source
    } else {
      streams.log.debug("No JNI sources found, skipping ndk-build")
      Seq.empty
    }
  }

  private val ndkBuildTask =
    (ndkBuildPath, ndkUnmanagedEnv, javahOutputEnv, javahOutputDirectory,
      ndkNativeObjectPath, ndkNativeOutputPath, ndkJniSourcePath, ndkEnvs, streams) map (
      runNdkBuild(_, _, _, _, _, _, _, _, _)
    )

  private val ndkCleanTask =
    (ndkBuildPath, ndkUnmanagedEnv, javahOutputEnv, javahOutputDirectory,
      ndkNativeObjectPath, ndkNativeOutputPath, ndkJniSourcePath, ndkEnvs, streams) map {
      (bp, ue, joe, jod, nob, nou, nj, env, s) =>
      runNdkBuild(bp, ue, joe, jod, nob, nou, nj, env, s, "clean"); ()
    }

  lazy val settings: Seq[Setting[_]] = pathSettings ++ Seq (

    // Header generation
    javah <<= (
        (compile),
        javahPath,
        (classDirectory), (internalDependencyClasspath), (externalDependencyClasspath),
        jniClasses,
        javahOutputDirectory, javahOutputFile,
        streams) map ((
            _, // we only depend on a side effect (built classes) of compile
            javahPath,
            classDirectory, internalDependencyClasspath, externalDependencyClasspath,
            jniClasses,
            javahOutputDirectory,
            javahOutputFile,
            streams) =>
        javahTask(
          javahPath,
          Seq(classDirectory) ++ internalDependencyClasspath.files ++ externalDependencyClasspath.files,
          jniClasses,
          javahOutputDirectory, javahOutputFile,
          streams)
    	),

    // NDK build task
    ndkBuild <<= ndkBuildTask,
    ndkBuild <<= ndkBuild dependsOn javah,

    // Add ndk-build to the build products
    nativeDirectories <++= ndkBuild,

    // Clean tasks
    javahClean <<= (javahOutputDirectory) map IO.delete,
    ndkClean <<= ndkCleanTask,
    clean <<= clean.dependsOn(ndkClean, javahClean)
  )
}
