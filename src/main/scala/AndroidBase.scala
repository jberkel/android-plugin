import sbt._

import Keys._
import AndroidKeys._
import AndroidHelpers._

object AndroidBase {

  private def resDirsWithDepsTask = 
    (thisProject, mainResPath, Keys.settings) map {
      ( thisProj, thisProjResPath, settings ) => {

        // Find dependent projects with resources of their own, and
        // collect the resource directories.

        def androidSetting[T]( aKey:SettingKey[T], projectRef: ProjectRef )=
          ((aKey in Android) in (projectRef)) get settings

        val depResPaths: Seq[java.io.File] = 
          for (dep        <- thisProj.dependencies;
               depResPath <- androidSetting( mainResPath, dep.project ))
          yield depResPath
      
        depResPaths :+ thisProjResPath
    }}

  private def aaptGenerateTask =
    (manifestPackage, aaptPath, manifestPath, resDirsWithDeps, jarPath, managedJavaPath,
     thisProject, Keys.settings) map {
    (mPackage, aPath, mPath, resPaths, jPath, javaPath, thisProj, settings) => {

      // Find dependent projects that have settings that look like
      // they need an R.java.  Presumably, they only need their own
      // resources, but we give them the whole set, because that's
      // the only way to make the IDs consistent

      def androidSetting[T]( androidKey:SettingKey[T], projectRef: ProjectRef )=
        ((androidKey in Android) in (projectRef)) get settings

      val aaptDepTargets = (
        for (dep             <- thisProj.dependencies;
             managedJavaPath <- androidSetting( managedJavaPath, dep.project );
             projPackage     <- androidSetting( manifestPackage, dep.project ))
          yield ( managedJavaPath, projPackage ))
            
      val aaptAllTargets = aaptDepTargets :+ (javaPath, mPackage)

      for (( path, pkg ) <- aaptAllTargets) { 
        IO.createDirectory( path ) 
      }

      val resPathArgs = resPaths.map{"-S "+_.absolutePath+" "}.reduceLeft( _+_ )

      val processes =
        for (( projManagedJavaPath, projPackage ) <- aaptAllTargets)
        yield Process (<x>
                       {aPath.absolutePath} package --auto-add-overlay -m
                       --custom-package {projPackage}
                       -M {mPath.absolutePath}
                       {resPathArgs}
                       -I {jPath.absolutePath}
                       -J {projManagedJavaPath.absolutePath}
                       </x>)

      val status = processes.reduceLeft{ _ #&& _ } !

      if (status > 0) {
        error( "aapt failed; consult output for possible reasons." )
      }

      for (( path, pkg ) <- aaptAllTargets;
           file <- ( path ** "R.java" ) get )
        yield file
    }}

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

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    platformPath <<= (sdkPath, platformName) (_ / "platforms" / _),

    packageApkName <<= (artifact, version) ((a, v) => String.format("%s-%s.apk", a.name, v)),
    manifestPath <<= (sourceDirectory, manifestName) (_ / _),
    manifestTemplatePath <<= (sourceDirectory, manifestName) (_ / _),

    manifestPackage <<= (manifestTemplatePath) {
      manifest(_).attribute("package").getOrElse(error("package not defined")).text
    },
    minSdkVersion <<= (manifestTemplatePath, manifestSchema)(usesSdk(_, _, "minSdkVersion")),
    maxSdkVersion <<= (manifestTemplatePath, manifestSchema)(usesSdk(_, _, "maxSdkVersion")),

    nativeLibrariesPath <<= (sourceDirectory) (_ / "libs"),
    mainAssetsPath <<= (sourceDirectory, assetsDirectoryName) (_ / _),
    mainResPath <<= (sourceDirectory, resDirectoryName) (_ / _),
    managedJavaPath <<= (target) (_ / "src_managed" / "main" / "java"),

    classesMinJarPath <<= (target, classesMinJarName) (_ / _),
    classesDexPath <<= (target, classesDexName) (_ / _),
    resourcesApkPath <<= (target, resourcesApkName) (_ / _),
    packageApkPath <<= (target, packageApkName) (_ / _),
    useProguard := true,

    addonsJarPath <<= (manifestTemplatePath, manifestSchema, mapsJarPath) {
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
      (libraryJarPath, classDirectory, resourceDirectory, unmanagedClasspath in Compile) map {
        (libPath, classDirectory, resourceDirectory, unmanagedClasspath) =>
          val temp = libPath +++ classDirectory +++ resourceDirectory
          unmanagedClasspath.foldLeft(temp)(_ +++ _.data) get
      },
    proguardInJars <<= (fullClasspath, proguardExclude) map {
      (runClasspath, proguardExclude) =>
      runClasspath.map(_.data) --- proguardExclude get
    },

    makeManagedJavaPath <<= directory(managedJavaPath),
    resDirsWithDeps <<= resDirsWithDepsTask,

    aaptGenerate <<= aaptGenerateTask,
    aidlGenerate <<= aidlGenerateTask,

    unmanagedJars in Compile <++= (libraryJarPath) map (_.map(Attributed.blank(_))),

    sourceGenerators in Compile <+= (aaptGenerate, aidlGenerate) map (_ ++ _),

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
