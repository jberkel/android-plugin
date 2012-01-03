import sbt._

import Keys._
import AndroidKeys._
import AndroidHelpers._

import sbinary.DefaultProtocol.StringFormat

object AndroidBase {

  private def aaptGenerateTask =
    (manifestPackage, aaptPath, manifestPath, mainResPath, jarPath, managedJavaPath) map {
    (mPackage, aPath, mPath, resPath, jPath, javaPath) =>

    Seq(aPath.absolutePath, "package", "--auto-add-overlay", "-m",
        "--custom-package", mPackage,
        "-M", mPath.head.absolutePath,
        "-S", resPath.absolutePath,
        "-I", jPath.absolutePath,
        "-J", javaPath.absolutePath) !

    javaPath ** "R.java" get
  }

  private def aidlGenerateTask =
    (sourceDirectories, idlPath, managedJavaPath, javaSource, streams) map {
    (sDirs, idPath, javaPath, jSource, s) =>
    val aidlPaths = sDirs.map(_ ** "*.aidl").reduceLeft(_ +++ _).get
    if (aidlPaths.isEmpty) {
      s.log.debug("no AIDL files found, skipping")
      Nil
    } else {
      val processor = aidlPaths.map { ap =>
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
      s.log.debug("generating aidl "+processor)
      processor !

      val rPath = javaPath ** "R.java"
      javaPath ** "*.java" --- (rPath) get
    }
  }

  def findPath() = (manifestPath) map { p =>
      manifest(p.head).attribute("package").getOrElse(sys.error("package not defined")).text
  }

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    platformPath <<= (sdkPath, platformName) (_ / "platforms" / _),

    packageApkName <<= (artifact, version) ((a, v) => String.format("%s-%s.apk", a.name, v)),
    manifestPath <<= (sourceDirectory, manifestName) map((s,m) => Seq(s / m)),

    manifestPackage <<= findPath,
    manifestPackageName <<= findPath storeAs manifestPackageName triggeredBy manifestPath,

    minSdkVersion <<= (manifestPath, manifestSchema) map ( (p,s) => usesSdk(p.head, s, "minSdkVersion")),
    maxSdkVersion <<= (manifestPath, manifestSchema) map ( (p,s) => usesSdk(p.head, s, "maxSdkVersion")),

    nativeLibrariesPath <<= (sourceDirectory) (_ / "libs"),
    mainAssetsPath <<= (sourceDirectory, assetsDirectoryName) (_ / _),
    mainResPath <<= (sourceDirectory, resDirectoryName) (_ / _) map (x=> x),
    managedJavaPath <<= (managedSourceDirectories in Compile) (_.head / "java"),

    classesMinJarPath <<= (target, classesMinJarName) (_ / _),
    classesDexPath <<= (target, classesDexName) (_ / _),
    resourcesApkPath <<= (target, resourcesApkName) (_ / _),
    packageApkPath <<= (target, packageApkName) (_ / _),
    useProguard := true,
    proguardOptimizations := Seq.empty,

    apiLevel <<= (minSdkVersion, platformName) map { (min, pName) =>
      min.getOrElse(platformName2ApiLevel(pName))
    },

    jarPath <<= (platformPath, jarName) (_ / _),
    libraryJarPath <<= (jarPath (_ get)),

    proguardOption := "",
    proguardExclude <<= (libraryJarPath, classDirectory, resourceDirectory) map {
        (libPath, classDirectory, resourceDirectory) =>
          libPath :+ classDirectory :+ resourceDirectory
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

    resourceDirectories <+= (mainAssetsPath),

    cachePasswords := false
  ) ++ Seq (
    // Handle the delegates for android settings
    classDirectory <<= (classDirectory in Compile),
    sourceDirectory <<= (sourceDirectory in Compile),
    sourceDirectories <<= (sourceDirectories in Compile),
    resourceDirectory <<= (resourceDirectory in Compile),
    resourceDirectories <<= (resourceDirectories in Compile),
    javaSource <<= (javaSource in Compile),
    managedClasspath <<= (managedClasspath in Runtime),
    fullClasspath <<= (fullClasspath in Runtime)
  ))
}
