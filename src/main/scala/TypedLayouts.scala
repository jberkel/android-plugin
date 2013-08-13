package sbtandroid

import sbt.{stringToProcess => _, _}
import classpath._
import scala.xml._

import Keys._
import AndroidPlugin._

// FIXME more appropriate name
object TypedLayouts {
  /** Views with android:id. */
  private case class NamedView(id: String,
                               className: String,
                               subViews: Iterable[NamedView]) {
    def flatten: Iterable[NamedView] = {
      Iterable(this) ++ subViews.flatMap(_.flatten)
    }
  }

  /** Returns tree nodes represents views with android:id */
  private def buildTree(jarPath: File)(element: Elem): Iterable[NamedView] = {
    val androidJarLoader = ClasspathUtilities.toLoader(jarPath)

    /** Returns a class object if exits. */
    def tryLoading(className: String): Option[Class[_]] = {
      try {
        Some(androidJarLoader.loadClass(className))
      } catch {
        case e: Exception => None
        case e: LinkageError => None
      }
    }

    def attributeText(element: Elem,
                      namespace: String,
                      localName: String) = {
      element
        .attribute(namespace, localName)
        .map(_.map(_.text).mkString)
    }

    val androidNamespace = "http://schemas.android.com/apk/res/android"

    /** regexp extracting id */
    val Id = """@\+id/(.*)""".r

    val idOption =
      attributeText(element, androidNamespace, "id")
        .collect({ case Id(id) => id })

    // node.label is either fully qualified class name or
    // unqualified class name.
    val classNameOption = if (element.label.contains('.')) {
      Some(element.label)
    } else {
      List("android.widget.", "android.view.", "android.webkit.")
        .map(_ + element.label)
        .flatMap(tryLoading _)
        .headOption
        .map(_.getName)
        .map("_root_." + _)
    }

    val pairOption = for {
      id <- idOption
      className <- classNameOption
    } yield {
      (id, className)
    }

    val subViews =
      element.child.collect({ case e: Elem => e }).flatMap(buildTree(jarPath) _)

    pairOption match {
      case Some((id, className)) =>
        Seq(NamedView(id, className, subViews))
      case None =>
        subViews
    }
  }

  /** Merges layout definitions having the same name. */
  private def mergeLayouts(streams: std.TaskStreams[Project.ScopedKey[_]])
                          (layouts: Iterable[(String, Iterable[NamedView])]) = {
    def mergeViews(views: Iterable[NamedView]): Iterable[NamedView] = {
      views.groupBy(_.id).values.map(doMergeViews _)
    }

    def doMergeViews(views: Iterable[NamedView]): NamedView = {
      val id = views.head.id
      val className = views.head.className

      for (view <- views) {
        if (view.className != className) {
          streams.log.warn("Resource id '%s' mapped to %s and %s"
                             .format(id, className, view.className))
        }
      }

      NamedView(id, className, mergeViews(views.flatMap(_.subViews)))
    }

    def toMultimap[A, B](tuples: Iterable[(A, B)]) = {
      tuples.groupBy(_._1).mapValues(_.map(_._2))
    }

    toMultimap(layouts).mapValues(_.flatten).mapValues(mergeViews _)
  }

  /** Reserved words of Scala language */
  // from Scala Language Specification Version 2.9 Section 1.1
  private val reserved =
    Set("abstract", "case", "do", "else", "finally", "for", "import",
        "lazy", "object", "override", "return", "sealed", "trait", "try",
        "var", "while", "catch", "class", "extends", "false", "forSome",
        "if", "match", "new", "package", "private", "super", "this",
        "true", "type", "with", "yield", "def", "final", "implicit",
        "null", "protected", "throw", "val")

  /** Quotes a qualified/unqualified identifier if it is reserved */
  private def quoteReserved(id: String) = {
    id.split("\\.").map(id =>
      if (reserved.contains(id)) {
        "`" + id + "`"
      } else {
        id
      }
    ).mkString(".")
  }

  private def toCamelCase(name: String) = {
    "_(.)".r.replaceAllIn(name, _.group(1).toUpperCase)
  }

  private def toUpperCamelCase(name: String) = {
    toCamelCase(name).capitalize
  }

  // FIXME Name clashes occur if duplicated IDs exist.
  private def formatLayout(manifestPackage: String)
                          (layout: (String, Iterable[NamedView])) = {
    val (layoutName, views) = layout

    // Since Activity, Dialog, and View does not have a common interface,
    // we generate four types: common trait, base trait for Activity,
    // base trait for Dialog, and view wrapper suitable for use with such as
    // SimplerAdapter.ViewBinder.
    """|trait %1$s {
       |  // to avoid name shadowing in pathological cases.
       |  `this` =>
       |
       |  def findViewById(id: _root_.scala.Int): _root_.android.view.View
       |
       |
       |  %2$s
       |
       |}
       |
       |object %1$s {
       |  trait Activity extends _root_.android.app.Activity with %4$s.Layout.%1$s {
       |    protected override def onCreate(savedInstanceState: _root_.android.os.Bundle) {
       |      super.onCreate(savedInstanceState)
       |
       |      setContentView(%4$s.R.layout.%3$s)
       |    }
       |  }
       |
       |  trait Dialog extends _root_.android.app.Dialog with %4$s.Layout.%1$s {
       |    protected override def onCreate(savedInstanceState: _root_.android.os.Bundle) {
       |      super.onCreate(savedInstanceState)
       |
       |      setContentView(%4$s.R.layout.%3$s)
       |    }
       |  }
       |
       |  class ViewWrapper(view: _root_.android.view.View) extends %4$s.Layout.ViewWrapper(view) with %4$s.Layout.%1$s {
       |    override def findViewById(id: _root_.scala.Int) = {
       |      view.findViewById(id)
       |    }
       |  }
       |
       |  def apply(view: _root_.android.view.View) = new ViewWrapper(view)
       |}
       |""".stripMargin.format(toUpperCamelCase(layoutName),
                               views
                                 .flatMap(_.flatten)
                                 .map(formatView(manifestPackage)(layoutName, _))
                                 .mkString("\n")
                                 .lines
                                 .mkString("\n    "),
                               quoteReserved(layoutName),
                               "_root_." + manifestPackage)
  }

  private def formatView(manifestPackage: String)
                        (layoutName: String, view: NamedView) = {
    if (view.subViews.isEmpty) {
      "lazy val %1$s = `this`.findViewById(%3$s.R.id.%1$s).asInstanceOf[%2$s]"
        .format(quoteReserved(view.id),
                quoteReserved(view.className),
                "_root_." + manifestPackage)
    } else {
      """|object %1$s extends %4$s.Layout.ViewWrapper[%2$s](`this`.findViewById(%4$s.R.id.%1$s).asInstanceOf[%2$s]) {
         |  object views {
         |    %3$s
         |  }
         |}
         |""".stripMargin.format(quoteReserved(view.id),
                                 quoteReserved(view.className),
                                 view
                                   .subViews
                                   .flatMap(_.flatten)
                                   .map(formatSubView(layoutName, _))
                                   .mkString("\n")
                                   .lines
                                   .mkString("\n    "),
                                 "_root_." + manifestPackage
      )
    }
  }

  private def formatSubView(layoutName: String, view: NamedView) = {
    "def %1$s = `this`.views.%1$s".format(quoteReserved(view.id))
  }

  private def generateTypedLayoutsTask =
    (useTypedLayouts, typedLayouts, layoutResources, libraryJarPath, manifestPackage, streams) map {
    (useTypedLayouts, typedLayouts, layoutResources, libraryJarPath, manifestPackage, s) =>
      if (useTypedLayouts) {
        // e.g. main_activity.xml -> main_activity
        def baseName(path: File) = {
          val name = path.getName

          name.substring(0, name.lastIndexOf("."))
        }

        val layouts = mergeLayouts(s)(
          layoutResources.get.map(path =>
            (baseName(path), buildTree(libraryJarPath)(XML.loadFile(path)))
          )
        )

        IO.write(typedLayouts,
                 """|package %s
                    |
                    |object Layout {
                    |  case class ViewWrapper[A <: _root_.android.view.View](view: A)
                    |  object ViewWrapper {
                    |    implicit def unwrap[A <: _root_.android.view.View](v: ViewWrapper[A]): A = v.view
                    |  }
                    |
                    |  %s
                    |
                    |}
                    |"""
                   .stripMargin.format(manifestPackage,
                                       layouts
                                         .map(formatLayout(manifestPackage) _)
                                         .mkString("\n")
                                         .lines
                                         .mkString("\n    ")
                 )
        )
        s.log.info("Wrote %s".format(typedLayouts))
        Seq(typedLayouts)
      } else {
        Seq.empty
      }
    }

  lazy val settings: Seq[Setting[_]] = (Seq (
    typedLayouts <<= (manifestPackage, managedScalaPath) map {
      _.split('.').foldLeft(_) ((p, s) => p / s) / "typed_layouts.scala"
    },
    layoutResources <<= (mainResPath) map { x => (x * "layout*" * "*.xml" get) },

    generateTypedLayouts <<= generateTypedLayoutsTask,

    sourceGenerators <+= generateTypedLayouts,

    watchSources <++= (layoutResources) map (ls => ls)
  ))
}
