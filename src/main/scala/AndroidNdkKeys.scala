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
  val libsDirectoryName = SettingKey[String]("libs", "Directory name for compiled native libraries.")
  val ndkEnvs = SettingKey[Seq[String]]("ndk-envs", "List of environment variables to check for the NDK.")

  val jniSourcePath = SettingKey[File]("jni-source-path")
  val nativeOutputPath = SettingKey[File]("native-output-path")
  val nativeObjectPath = SettingKey[File]("native-object-path")
  val ndkBuildPath = SettingKey[File]("ndk-build-path")
  
  val ndkBuild = TaskKey[Unit]("ndk-build", "Compile native C/C++ sources.")
  val ndkClean = TaskKey[Unit]("ndk-clean", "Clean resources built from native C/C++ sources.")

}