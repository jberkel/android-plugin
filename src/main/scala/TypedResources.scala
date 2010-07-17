import sbt._
import scala.xml._

trait TypedResources extends AndroidProject {
  def managedScalaPath = "src_managed" / "main" / "scala"
  /** Typed resource file to be generated, also includes interfaces to access these resources. */
  def typedResource = managedScalaPath / "TR.scala"
  abstract override def mainSourceRoots = super.mainSourceRoots +++ managedScalaPath
  def layoutResources = mainResPath / "layout" ** "*.xml"
  override def compileAction = super.compileAction dependsOn generateTypedResources
  override def cleanAction = super.cleanAction dependsOn cleanTask(managedScalaPath)
  override def watchPaths = super.watchPaths +++ layoutResources
  
  /** File task that generates `typedResource` if it's older than any layout resource, or doesn't exist */
  lazy val generateTypedResources = fileTask(typedResource from layoutResources) {
    val Id = """@\+id/(.*)""".r
    val androidJarLoader = ClasspathUtilities.toLoader(androidJarPath)
    val resources = layoutResources.get.flatMap { path =>
      XML.loadFile(path.asFile).descendant_or_self flatMap { node =>
        // all nodes
        node.attribute("http://schemas.android.com/apk/res/android", "id") flatMap {
          // with android:id attribute
          _.firstOption map { _.text } flatMap {
            // if it looks like a full classname
            case Id(id) if node.label.contains('.') => Some(id, node.label)
            // otherwise it may be a widget
            case Id(id) => try { Some(id,
              androidJarLoader.loadClass("android.widget." + node.label).getName
            ) } catch { case _ => None }
            case _ => None
          }
        }
      }
    }.foldLeft(Map.empty[String, String]) { 
      case (m, (k, v)) => 
        m.get(k).foreach { v0 =>
          if (v0 != v) log.warn("Resource id '%s' mapped to %s and %s" format (k, v0, v))
        }
        m + (k -> v)
    }
    FileUtilities.write(typedResource.asFile,
    """     |package %s
            |import android.app.Activity
            |import android.view.View
            |
            |case class TypedResource[T](id: Int)
            |object TR {
            |%s
            |}
            |trait TypedViewHolder {
            |  def view: View
            |  def findView[T](tr: TypedResource[T]) = view.findViewById(tr.id).asInstanceOf[T]  
            |}
            |trait TypedView extends View with TypedViewHolder { def view = this }
            |trait TypedActivityHolder {
            |  def activity: Activity
            |  def findView[T](tr: TypedResource[T]) = activity.findViewById(tr.id).asInstanceOf[T]
            |}
            |trait TypedActivity extends Activity with TypedActivityHolder { def activity = this }
            |object TypedResource {
            |  implicit def view2typed(v: View) = new TypedViewHolder { def view = v }
            |  implicit def activity2typed(act: Activity) = new TypedActivityHolder { def activity = act }
            |}
            |""".stripMargin.format(
              manifestPackage, resources map { case (id, classname) =>
                "  val %s = TypedResource[%s](R.id.%s)".format(id, classname, id)
              } mkString "\n"
            ), log
    )
    None
  } describedAs ("Produce a file TR.scala that contains typed references to layout resources")
}