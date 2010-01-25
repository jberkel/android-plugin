import sbt._
import Process._

trait MarketPublish extends AndroidProject {
  /** Keystore alias for the private key used to sign this application */
  def key_alias: String
  
  lazy val prepareMarket = prepareMarketAction
  def prepareMarketAction = task { 
    log.success("Ready for publication: \n" + packageAlignedPath)
    None
  } dependsOn(zipAlign) describedAs("Prepare asset for Market publication.")
  
  def zipAlignPath = androidToolsPath / "zipalign"
  def packageAlignedName = artifactBaseName + "-market" + ".apk"
  def packageAlignedPath = outputPath / packageAlignedName

  lazy val cleanAligned = cleanTask(packageAlignedPath) describedAs("Remove zipaligned jar")

  lazy val zipAlign = zipAlignAction
  def zipAlignAction = execTask {<x>
      {zipAlignPath} -v  4 {packageApkPath} {packageAlignedPath}
  </x>} dependsOn(signRelease, cleanAligned) describedAs("Run zipalign on signed jar.")

  lazy val signRelease = signReleaseAction
  def signReleaseAction = execTask {<x>
      jarsigner -verbose -storepass {getPassword} {packageApkPath} {key_alias}
  </x>} dependsOn(packageRelease) describedAs(
    "Sign with key alias '%s' in ~/.keystore, using jarsigner." format key_alias)
  
  def getPassword = SimpleReader.readLine("\nEnter keystore password: ").get
}