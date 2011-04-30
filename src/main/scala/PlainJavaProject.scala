/** Some sensible defaults for building java projects with Android plugin */
trait PlainJavaProject extends AndroidProject {
    override def skipProguard = true
    override def androidManifestPath = androidManifestName
    override def mainResPath = resDirectoryName
    override def mainJavaSourcePath = "src"
}
