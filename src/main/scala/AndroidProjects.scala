package org.scalasbt.androidplugin

import sbt._
import Keys._
import Defaults._
import AndroidPlugin._

object AndroidProjects {

  object Test {

    /**
     * Default test project settings
     */
    lazy val defaults =
      AndroidBase.globalSettings ++
      inConfig(Compile)(
        AndroidBase.settings ++
        AndroidManifestGenerator.settings ++
        AndroidPreload.settings ++
        AndroidInstall.settings ++
        AndroidTest.settings ++
        AndroidDdm.settings ++
        TypedResources.settings ++
        Seq(
          // Test projects are set to Debug projects by default
          useDebug := true,

          // Test projects are usually pretty small, so no need for Proguard
          useProguard := false,

          // The Scala library is already imported by the main project
          usePreloadedScala := false
        )
      )

    /**
     * Create a test Android project.
     *
     * See sbt.Project.apply definition:
     *    http://www.scala-sbt.org/release/api/sbt/Project$.html
     */
    def apply(
      id: String,
      base: File,
      aggregate: => Seq[ProjectReference] = Nil,
      dependencies: => Seq[ClasspathDep[ProjectReference]] = Nil,
      delegates: => Seq[ProjectReference] = Nil,
      settings: => Seq[sbt.Project.Setting[_]] = Seq.empty,
      configurations: Seq[Configuration] = Configurations.default
    ) = Project(id,
                base,
                aggregate,
                dependencies,
                delegates,
                defaultSettings ++ defaults ++ settings,
                configurations)
  }

  object Standard {

    // Default Android settings for standard projects
    // Standard presets :
    //
    //  * androidDevelopment:
    //        Does not include the Scala library, skips Proguard,
    //        predexes external libraries and automatically sets
    //        AndroidManifest.xml to require a preloaded Scala library.
    //
    //        NOTE: The generated APK will NOT be compatible with stock devices
    //              without a preloaded Scala library!
    //
    //  * androidDebug: Will generate a debug APK compatible with stock Android devices.
    //  * androidRelease: Will generate a realeas APK compatible with stock Android devices.

    /**
     * Standard Android defaults
     */
    lazy val androidDefaults: Seq[Setting[_]] = {
      AndroidBase.settings ++
      AndroidManifestGenerator.settings ++
      AndroidPreload.settings ++
      AndroidInstall.settings ++
      AndroidLaunch.settings ++
      AndroidDdm.settings ++
      TypedResources.settings
    }

    // Development settings
    lazy val androidDevelopment: Seq[Setting[_]] = {
      androidDefaults ++ Seq(
        useDebug := true,
        useProguard := false,
        usePreloadedScala := true
      )
    }

    // Debug settings
    lazy val androidDebug: Seq[Setting[_]] = {
      androidDefaults ++ Seq(
        useDebug := true,
        useProguard := true,
        usePreloadedScala := false
      )
    }

    // Release settings
    lazy val androidRelease: Seq[Setting[_]] = {
      androidDefaults ++ Seq(
        useDebug := false,
        useProguard := true,
        usePreloadedScala := false
      )
    }

    /**
     * Default settings
     */
    lazy val defaults =
      AndroidBase.globalSettings ++
      inConfig(Compile)(androidDevelopment) ++
      inConfig(Debug)(compileSettings ++ androidDebug) ++
      inConfig(Release)(compileSettings ++ androidRelease)

   /**
    * Create a default Android project, already set up for standard
    * development.
    *
    * See sbt.Project.apply definition:
    *    http://www.scala-sbt.org/release/api/sbt/Project$.html
    */
    def apply(
      id: String,
      base: File,
      aggregate: => Seq[ProjectReference] = Nil,
      dependencies: => Seq[ClasspathDep[ProjectReference]] = Nil,
      delegates: => Seq[ProjectReference] = Nil,
      settings: => Seq[sbt.Project.Setting[_]] = Seq.empty,
      configurations: Seq[Configuration] = Configurations.default
    ) = Project(id,
                base,
                aggregate,
                dependencies,
                delegates,
                defaultSettings ++ defaults ++ settings,
                configurations ++ Seq(Debug, Release))
  }
}
