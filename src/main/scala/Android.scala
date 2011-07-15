import sbt._
import Keys._

import AndroidKeys._
import AndroidHelpers._

object AndroidProject extends Plugin {

  lazy val defaultAliases = Seq (
    startEmulator, installEmulator, reinstallEmulator, uninstallEmulator,
    startDevice, installDevice, reinstallDevice, uninstallDevice, listDevices
  )

  override lazy val settings = inConfig(Android)(
    AndroidDefaults.settings ++
    AndroidBase.settings ++
    AndroidInstall.settings ++ 
    AndroidLaunch.settings ++ 
    AndroidDdm.settings ++ Seq (
    // Handle the delegates for android settings
    classDirectory <<= (classDirectory in Compile).identity,
    sourceDirectory <<= (sourceDirectory in Compile).identity,
    sourceDirectories <<= (sourceDirectories in Compile).identity,
    resourceDirectory <<= (resourceDirectory in Compile).identity,
    resourceDirectories <<= (resourceDirectories in Compile).identity,
    javaSource <<= (javaSource in Compile).identity,
    managedClasspath <<= (managedClasspath in Runtime).identity,
    fullClasspath <<= (fullClasspath in Runtime).identity
  )) ++ defaultAliases.map (
    k => k <<= (k in Android).identity
  )
}
