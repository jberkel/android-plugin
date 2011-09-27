import sbt._
import Keys._
import AndroidKeys._

/** Some sensible defaults for building java projects with the plugin */
object PlainJavaProject {
  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq(
    useProguard := false,
    manifestPath <<= (baseDirectory, manifestName) map((s,m) => Seq(s / m)),
    mainResPath <<= (baseDirectory, resDirectoryName) (_ / _),
    javaSource in Compile <<= (baseDirectory) (_ / "src")
    )
  )
}
