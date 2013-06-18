package sbtandroid

import sbt._

import Keys._
import AndroidPlugin._
import AndroidHelpers._

object AndroidPath {
  private def determineBuildToolsVersion(sdkPath: File): Option[String] = {
    // Find out which versions of the build tools are installed
    val buildToolsPath = (sdkPath / "build-tools")

    // If this path doesn't exist, just set the version to ""
    if (!buildToolsPath.exists) None

    // Else, sort the installed versions and take the most recent
    else {
      // List the versions
      val files = buildToolsPath.listFiles

      // If the directory is empty, return None
      if (files.isEmpty) None

      // Else, find the latest version
      else Some(buildToolsPath.listFiles.map(_.name).reduceLeft((s1, s2) => {

        // Convert the version numbers to arrays of integers
        val v1 = s1 split '.' map (_.toInt)
        val v2 = s2 split '.' map (_.toInt)

        // TODO: Make that better. Will crash if the version numbers don't
        //       have 3 digits (like 17.0.0)...

        // Compare them
        if((v1(0) > v2(0)) ||
          (v1(0) == v2(0) && v1(1) > v2(1)) ||
          (v1(0) == v2(0) && v1(1) == v2(1) && v1(2) > v2(1)))
          s1 else s2
      }))
    }
  }

  lazy val settings: Seq[Setting[_]] = {
    AndroidDefaults.settings ++ Seq (
      osDxName <<= (dxName) (_ + osBatchSuffix),

      toolsPath <<= (sdkPath) (_ / "tools"),
      dbPath <<= (platformToolsPath, adbName) (_ / _),
      platformToolsPath <<= (sdkPath) (_ / "platform-tools"),
      buildToolsVersion <<= (sdkPath) (determineBuildToolsVersion _),
      buildToolsPath <<= (sdkPath, platformToolsPath, buildToolsVersion) { (sdkPath, platformToolsPath, buildToolsVersion) =>
        buildToolsVersion match {
          case Some(v) => sdkPath / "build-tools" / v
          case None => platformToolsPath
        }
      },
      aaptPath <<= (buildToolsPath, aaptName) (_ / _),
      idlPath <<= (buildToolsPath, aidlName) (_ / _),
      dxPath <<= (buildToolsPath, osDxName) (_ / _),

      sdkPath <<= (envs, baseDirectory) { determineAndroidSdkPath(_, _) },

      // Add the Google repository
      resolvers <+= (sdkPath) { p => "Google Repository" at (
        (p / "extras" / "google" / "m2repository").toURI.toString) },
      resolvers <+= (sdkPath) { p => "Android Support Repository" at (
        (p / "extras" / "android" / "m2repository").toURI.toString) }
    )
  }
}
