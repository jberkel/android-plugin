import sbt._

import Keys._
import AndroidKeys._
import AndroidHelpers._

object AndroidBase {

  private def aaptGenerateTask =
    (manifestPackage, aaptPath, manifestPath, mainResPath, jarPath, managedJavaPath) map {
    (mPackage, aPath, mPath, resPath, jPath, javaPath) =>
    Process (<x>
      {aPath.absolutePath} package --auto-add-overlay -m
        --custom-package {mPackage}
        -M {mPath.absolutePath}
        -S {resPath.absolutePath}
        -I {jPath.absolutePath}
        -J {javaPath.absolutePath}
    </x>) !

    javaPath ** "R.java" get
  }

  private def aidlGenerateTask =
    (sourceDirectories, idlPath, managedJavaPath, javaSource) map {
    (sDirs, idPath, javaPath, jSource) =>
    val aidlPaths = sDirs.map(_ * "*.aidl").reduceLeft(_ +++ _).get
    val processor = if (aidlPaths.isEmpty)
      Process(true)
    else
      aidlPaths.map { ap =>
        idPath.absolutePath ::
          "-o" + javaPath.absolutePath ::
          "-I" + jSource.absolutePath ::
          ap.absolutePath :: Nil
      }.foldLeft(None.asInstanceOf[Option[ProcessBuilder]]) { (f, s) =>
        f match {
          case None => Some(s)
          case Some(first) => Some(first #&& s)
        }
      }.get
    processor !

    val rPath = javaPath ** "R.java"
    javaPath ** "*.java" --- (rPath) get
  }

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    platformPath <<= (sdkPath, platformName) (_ / "platforms" / _),

    packageApkName <<= (artifact) (_.name + ".apk"),
    manifestPath <<= (sourceDirectory, manifestName) (_ / _),

    manifestPackage <<= (manifestPath) {
      manifest(_).attribute("package").getOrElse(error("package not defined")).text
    },
    minSdkVersion <<= (manifestPath, manifestSchema)(usesSdk(_, _, "minSdkVersion")),
    maxSdkVersion <<= (manifestPath, manifestSchema)(usesSdk(_, _, "maxSdkVersion")),

    nativeLibrariesPath <<= (sourceDirectory) (_ / "libs"),
    mainAssetsPath <<= (sourceDirectory, assetsDirectoryName) (_ / _),
    mainResPath <<= (sourceDirectory, resDirectoryName) (_ / _),
    managedJavaPath <<= (baseDirectory) (_ / "src_managed" / "main" / "java"),

    classesMinJarPath <<= (target, classesMinJarName) (_ / _),
    classesDexPath <<= (target, classesDexName) (_ / _),
    resourcesApkPath <<= (target, resourcesApkName) (_ / _),
    packageApkPath <<= (target, packageApkName) (_ / _),
    skipProguard := false,

    addonsJarPath <<= (manifestPath, manifestSchema, mapsJarPath) {
      (mPath, man, mapsPath) =>
      for {
        lib <- manifest(mPath) \ "application" \ "uses-library"
        p = lib.attribute(man, "name").flatMap {
          _.text match {
            case "com.google.android.maps" => Some(mapsPath)
            case _ => None
          }
        }
        if p.isDefined
      } yield p.get
    },

    apiLevel <<= (minSdkVersion, platformName) { (min, pName) =>
      min.getOrElse(platformName2ApiLevel(pName))
    },

    jarPath <<= (platformPath, jarName) (_ / _),
    mapsJarPath <<= (addonsPath) (_ / AndroidDefaults.DefaultMapsJarName),

    addonsPath <<= (sdkPath, apiLevel) { (sPath, api) =>
      sPath / "add-ons" / ("addon_google_apis_google_inc_" + api) / "libs"
    },

    libraryJarPath <<= (jarPath, addonsJarPath) (_ +++ _ get),

    proguardOption := "",
    proguardExclude <<=
      (libraryJarPath, classDirectory, resourceDirectory, managedClasspath) map {
        (libPath, classDirectory, resourceDirectory, managedClasspath) =>
          val temp = libPath +++ classDirectory +++ resourceDirectory
          managedClasspath.foldLeft(temp)(_ +++ _.data) get
      },
    proguardInJars <<= (fullClasspath, proguardExclude) map {
      (runClasspath, proguardExclude) =>
      runClasspath.map(_.data) --- proguardExclude get
    },

    makeManagedJavaPath <<= directory(managedJavaPath),

    aaptGenerate <<= aaptGenerateTask,
    aaptGenerate <<= aaptGenerate dependsOn makeManagedJavaPath,
    aidlGenerate <<= aidlGenerateTask,

    unmanagedJars in Compile <++= (libraryJarPath) map (_.map(Attributed.blank(_))),

    sourceGenerators in Compile <+= (aaptGenerate, aidlGenerate) map (_ ++ _),

    cleanFiles <+= (managedJavaPath).identity,
    resourceDirectories <+= (mainAssetsPath).identity
  ) ++ Seq (
    // Handle the delegates for android settings
    classDirectory <<= (classDirectory in Compile).identity,
    sourceDirectory <<= (sourceDirectory in Compile).identity,
    sourceDirectories <<= (sourceDirectories in Compile).identity,
    resourceDirectory <<= (resourceDirectory in Compile).identity,
    resourceDirectories <<= (resourceDirectories in Compile).identity,
    javaSource <<= (javaSource in Compile).identity,
    managedClasspath <<= (managedClasspath in Runtime).identity,
    fullClasspath <<= (fullClasspath in Runtime).identity
  ))
}
