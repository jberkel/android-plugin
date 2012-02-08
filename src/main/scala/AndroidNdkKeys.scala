import sbt._

import Keys._

/*!# Android NDK Keys
`AndroidNdkKeys` contains all the `SettingKey`s and `TaskKey`s for
Android projects using the NDK.
 */
object AndroidNdkKeys {
  val ndkBuildName = SettingKey[String]("ndk-build-name", "Name for the 'ndk-build' tool")
  val jniDirectoryName = SettingKey[String]("jni-directory-name", "Directory name for native sources.")
  val objDirectoryName =  SettingKey[String]("obj", "Directory name for compiled native objects.")
  val ndkEnvs = SettingKey[Seq[String]]("ndk-envs", "List of environment variables to check for the NDK.")

  val jniSourcePath = SettingKey[File]("jni-source-path", "Path to native sources. (with Android.mk)")
  val nativeOutputPath = SettingKey[File]("native-output-path")
  val nativeObjectPath = SettingKey[File]("native-object-path")
  val ndkBuildPath = SettingKey[File]("ndk-build-path", "Path to the 'ndk-build' tool")

  val ndkBuild = TaskKey[Unit]("ndk-build", "Compile native C/C++ sources.")
  val ndkClean = TaskKey[Unit]("ndk-clean", "Clean resources built from native C/C++ sources.")

  val javahName = SettingKey[String]("javah-name", "The name of the javah command for generating JNI headers")
  val javahPath = SettingKey[String]("javah-path", "The path to the javah executable")
  val javah = TaskKey[Unit]("javah", "Produce C headers from Java classes with native methods")
  val javahClean = TaskKey[Unit]("javah-clean", "Clean C headers built from Java classes with native methods")

  val javahOutputDirectory = SettingKey[File]("javah-output-directory",
      "The directory where JNI headers are written to.")
  val javahOutputFile = SettingKey[Option[File]]("javah-output-file",
      "filename for the generated C header, relative to javah-output-directory")
  val javahOutputEnv = SettingKey[String]("javah-output-env",
      "Name of the make environment variable to bind to the javah-output-directory")

  val jniClasses = SettingKey[Seq[String]]("jni-classes",
      "Fully qualified names of classes with native methods for which JNI headers are to be generated.")
}
