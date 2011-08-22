import sbt._
import classpath._
import scala.xml._

import Keys._
import AndroidKeys._

object TypedResources {
  private def generateTypedResourcesTask =
    (typedResource, layoutResources, jarPath, manifestPackage, streams) map {
    (typedResource, layoutResources, jarPath, manifestPackage, s) =>
      val Id = """@\+id/(.*)""".r
      val androidJarLoader = ClasspathUtilities.toLoader(jarPath)

      def tryLoading(className: String) = {
        try {
          Some(androidJarLoader.loadClass(className))
        } catch {
          case _ => None
        }
      }

      val resources = layoutResources.get.flatMap { path =>
        XML.loadFile(path).descendant_or_self flatMap { node =>
          // all nodes
          node.attribute("http://schemas.android.com/apk/res/android", "id") flatMap {
            // with android:id attribute
            _.headOption.map { _.text } flatMap {
              // if it looks like a full classname
              case Id(id) if node.label.contains('.') => Some(id, node.label)
              // otherwise it may be a widget or view
              case Id(id) => {
                List("android.widget.", "android.view.", "android.webkit.").map(pkg =>
                  tryLoading(pkg + node.label)).find(_.isDefined).flatMap(clazz => 
                    Some(id, clazz.get.getName)
                  )
              }
              case _ => None
            }
          }
        }
      }.foldLeft(Map.empty[String, String]) {
        case (m, (k, v)) =>
          m.get(k).foreach { v0 =>
            if (v0 != v) s.log.warn("Resource id '%s' mapped to %s and %s" format (k, v0, v))
          }
          m + (k -> v)
      }

      IO.write(typedResource,
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
            )
        )
        s.log.info("Wrote %s" format(typedResource))
        Seq(typedResource)
    }

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    managedScalaPath <<= (baseDirectory) ( _ / "src_managed" / "main" / "scala"),
    typedResource <<= (manifestPackage, managedScalaPath) { 
      _.split('.').foldLeft(_) ((p, s) => p / s) / "TR.scala"
    },
    layoutResources <<= (mainResPath) (_ / "layout" ** "*.xml" get),

    generateTypedResources <<= generateTypedResourcesTask,

    sourceGenerators in Compile <+= generateTypedResources.identity,
    watchSources in Compile <++= (layoutResources) map (ls => ls)
  )) ++ Seq (
    cleanFiles <+= (managedScalaPath in Android).identity,
    generateTypedResources <<= (generateTypedResources in Android).identity
  )
}
