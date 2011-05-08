import sbt._

/** Some sensible defaults for building java projects with Android plugin */
trait PlainJavaProject extends AndroidProject {
    override def skipProguard = true
    override def androidManifestPath = androidManifestName
    override def mainResPath = resDirectoryName
    override def mainJavaSourcePath = "src"

    override def determineAndroidSdkPath = {
      val props = new java.util.Properties()
      try {
        val file = new java.io.File("local.properties")
        val reader = new java.io.FileReader(file)
        props.load(reader)
        reader.close
        val sdkDir = props.getProperty("sdk.dir", props.getProperty("sdk-location"))
        if (sdkDir != null) Some(Path.fromFile(sdkDir)) else super.determineAndroidSdkPath
      } catch {
        case e: java.io.IOException => super.determineAndroidSdkPath
      }
    }
}
