import sbt._
import Keys._
import AndroidKeys._
import AndroidHelpers._

object AndroidTest {
  def instrumentationTestAction(emulator: Boolean) = (dbPath, manifestPackage) map {
    (dbPath, manifestPackage) =>
      val action = "shell am instrument -w " + manifestPackage +
        "/android.test.InstrumentationTestRunner"
      adbTask(dbPath.absolutePath, emulator, action)
  }

  /**AndroidTestProject */
  lazy val androidSettings = settings ++ Seq(
    proguardInJars in Android := Nil
  )

  lazy val settings: Seq[Setting[_]] =
    AndroidBase.settings ++
      AndroidInstall.settings ++
      inConfig(Android)(Seq(
        testEmulator <<= instrumentationTestAction(true),
        testDevice <<= instrumentationTestAction(false)
      )) ++ Seq(
      testEmulator <<= (testEmulator in Android).identity,
      testDevice <<= (testDevice in Android).identity
    )

  lazy val Instrumentation = config("inst") extend (Android)

  val tasks: Seq[Setting[_]] = Seq(
    helloTask
  )

  lazy val instSettings: Seq[Setting[_]] =
    inConfig(Instrumentation)(Defaults.defaultSettings ++ Defaults.testSettings ++ AndroidBase.settings ++ AndroidProject.androidSettings ++
      Seq(
        manifestName <<= (manifestName in Android).identity,
        resDirectoryName <<= (resDirectoryName in Android).identity,
        assetsDirectoryName <<= (assetsDirectoryName in Android).identity,
        name <<= (name)("%s-test" format _),
        dbPath <<= (dbPath in Android).identity,
        manifestPath <<= (sourceDirectory, manifestName)(_ / _),
        mainAssetsPath <<= (sourceDirectory, assetsDirectoryName)(_ / _),
        mainResPath <<= (sourceDirectory, resDirectoryName)(_ / _),
        uninstallEmulator <<= (uninstallEmulator in Android).identity,
        packageDebug <<= (packageDebug in Android).identity,
        uninstallDevice <<= (uninstallDevice in Android).identity,

        packageConfig <<=
          (toolsPath in Android, packageApkPath in Instrumentation, resourcesApkPath in Instrumentation, classesDexPath in Instrumentation,
            nativeLibrariesPath in Instrumentation, classesMinJarPath in Instrumentation, resourceDirectory in Instrumentation)
            (ApkConfig(_, _, _, _, _, _, _))
      )) ++ inConfig(Android)(Seq(hello := helloTask))
  //
  //    makeAssetPath <<= directory(mainAssetsPath),
  //
  //    aaptPackage <<= aaptPackageTask,
  //    aaptPackage <<= aaptPackage dependsOn (makeAssetPath, dx),
  //    dx <<= dxTask,
  //    dx <<= dx dependsOn proguard,
  //
  //    cleanApk <<= (packageApkPath) map (IO.delete(_)),
  //
  //    proguard <<= proguardTask,
  //    proguard <<= proguard dependsOn (compile in Compile),
  //
  //    packageConfig <<=
  //      (toolsPath, packageApkPath, resourcesApkPath, classesDexPath,
  //       nativeLibrariesPath, classesMinJarPath, resourceDirectory)
  //      (ApkConfig(_, _, _, _, _, _, _)),
  //
  //    packageDebug <<= packageTask(true),
  //    packageRelease <<= packageTask(false)
  //  ) ++ Seq(packageDebug, packageRelease).map {
  //    t => t <<= t dependsOn (cleanApk, aaptPackage)
  //  })

  val hello = TaskKey[Unit]("hello", "Prints 'Hello World'")

  val helloTask = hello := {
    println("Hello World")
  }

}
