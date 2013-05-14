package org.scalasbt.androidplugin

import sbt._
import classpath._
import scala.xml._

import Keys._
import AndroidPlugin._

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

      val layouts: Set[String] = layoutResources.get.flatMap{ layout =>
        val Name = "(.*)\\.[^\\.]+".r
        layout.getName match {
          case Name(name) => Some(name)
          case _ => None
        }
      }.toSet
      val reserved = List("extends", "trait", "type", "val", "var", "with")

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
      }.filterNot {
        case (id, _) => reserved.contains(id)
      }

      IO.write(typedResource,
    """     |package %s
            |import _root_.android.app.{Activity, Dialog}
            |import _root_.android.view.View
            |
            |case class TypedResource[T](id: Int)
            |case class TypedLayout(id: Int)
            |
            |object TR {
            |%s
            | object layout {
            | %s
            | }
            |}
            |trait TypedViewHolder {
            |  def findViewById( id: Int ): View
            |  def findView[T](tr: TypedResource[T]) = findViewById(tr.id).asInstanceOf[T]
            |}
            |trait TypedView extends View with TypedViewHolder
            |trait TypedActivityHolder extends TypedViewHolder
            |trait TypedActivity extends Activity with TypedActivityHolder
            |trait TypedDialog extends Dialog with TypedViewHolder
            |object TypedResource {
            |  implicit def layout2int(l: TypedLayout) = l.id
            |  implicit def view2typed(v: View) = new TypedViewHolder { 
            |    def findViewById( id: Int ) = v.findViewById( id )
            |  }
            |  implicit def activity2typed(a: Activity) = new TypedViewHolder { 
            |    def findViewById( id: Int ) = a.findViewById( id )
            |  }
            |  implicit def dialog2typed(d: Dialog) = new TypedViewHolder { 
            |    def findViewById( id: Int ) = d.findViewById( id )
            |  }
            |}
            |""".stripMargin.format(
              manifestPackage,
              resources map { case (id, classname) =>
                "  val %s = TypedResource[%s](R.id.%s)".format(id, classname, id)
              } mkString "\n",
              layouts map { name =>
                " val %s = TypedLayout(R.layout.%s)".format(name, name)
              } mkString "\n"
            )
        )
        s.log.info("Wrote %s" format(typedResource))
        Seq(typedResource)
    }

  lazy val settings: Seq[Setting[_]] = inConfig(Android) (Seq (
    typedResource <<= (manifestPackage, managedScalaPath) map {
      _.split('.').foldLeft(_) ((p, s) => p / s) / "TR.scala"
    },
    layoutResources <<= (mainResPath) map { x=> (x * "layout*" * "*.xml" get) },

    generateTypedResources <<= generateTypedResourcesTask,

    sourceGenerators in Compile <+= generateTypedResources,
    watchSources in Compile <++= (layoutResources) map (ls => ls)
  )) ++ Seq (
    generateTypedResources <<= (generateTypedResources in Android)
  )
}
