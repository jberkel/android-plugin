package sbtandroid

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
      AndroidNdk.globalSettings ++
      inConfig(Compile)(
        AndroidBase.settings ++
        AndroidManifestGenerator.settings ++
        AndroidPreload.settings ++
        AndroidInstall.settings ++
        AndroidDdm.settings ++
        AndroidTest.settings ++
        AndroidNdk.settings ++
        TypedResources.settings ++
        TypedResources2.settings ++
        Seq(
          // Test projects are Debug projects by default
          useDebug := true,

          // Test projects are usually pretty small, so no need for Proguard
          useProguard := false,

          // The Scala library is already imported by the main project
          usePreloaded := false,

          // It's highly unlikely that you'll need typed resources in test
          // projects, but you can enable it afterwards anyway, if you really
          // need it.
          useTypedResources := false,
          useTypedResources2 := false
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
    //  * androidPreload:
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
    lazy val androidConfig: Seq[Setting[_]] = {
      AndroidBase.settings ++
      AndroidManifestGenerator.settings ++
      AndroidPreload.settings ++
      AndroidInstall.settings ++
      AndroidDdm.settings ++
      AndroidLaunch.settings ++
      AndroidNdk.settings ++
      TypedResources.settings ++
      TypedResources2.settings
    }

    // Development settings
    lazy val androidPreload: Seq[Setting[_]] = {
      androidConfig ++ Seq(
        useDebug := true,
        useProguard := false,
        usePreloaded := true
      )
    }

    // Debug settings
    lazy val androidDebug: Seq[Setting[_]] = {
      androidConfig ++ Seq(
        useDebug := true,
        useProguard := true,
        usePreloaded := false
      )
    }

    // Release settings
    lazy val androidRelease: Seq[Setting[_]] = {
      androidConfig ++
      AndroidRelease.settings ++ Seq(
        useDebug := false,
        useProguard := true,
        usePreloaded := false,
        release in Global <<= release
      )
    }

    /**
     * Default settings, with 3 configurations :
     *  - "Compile" will build a regular, Proguard-ed debug APK
     *  - "Preload" will build an APK using preloaded libraries
     *  - "Release" will build a release APK
     */
    lazy val defaults =
      AndroidBase.globalSettings ++
      AndroidNdk.globalSettings ++
      inConfig(Compile)(androidDebug) ++
      inConfig(Preload)(compileSettings ++ androidPreload) ++
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
                configurations ++ Seq(Preload, Release))
  }
}
