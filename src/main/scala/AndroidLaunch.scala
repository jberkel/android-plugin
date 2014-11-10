package sbtandroid

import sbt._

import Keys._
import AndroidPlugin._
import AndroidHelpers._

object AndroidLaunch {

  val startTask = (
    (adbTarget, dbPath, streams, manifestSchema, manifestPackage, manifestPath) map {
    (adbTarget, dbPath, streams, manifestSchema, manifestPackage, manifestPath) =>
      adbTarget.startApp(dbPath, streams, manifestSchema, manifestPackage, manifestPath)
      ()
    }
  )

  val debugTask = (
    (adbTarget, dbPath, streams, manifestSchema, manifestPackage, manifestPath) map {
    (adbTarget, dbPath, streams, manifestSchema, manifestPackage, manifestPath) =>
      adbTarget.debugApp(dbPath, streams, manifestSchema, manifestPackage, manifestPath)
      ()
    }
  )

  lazy val settings: Seq[Setting[_]] =
    AndroidInstall.settings ++
    (Seq (
      start <<= startTask,
      start <<= start dependsOn install,
      debug <<= debugTask,
      debug <<= debug dependsOn install
    ))
}
