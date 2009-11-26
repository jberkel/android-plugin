##Installation

Requires [sbt](http://simple-build-tool.googlecode.com/)

    $ git clone git://github.com/jberkel/android-plugin.git
    $ cd android-plugin
    $ sbt publish-local

##Usage

To use the plugin in a project, you just need to create project/plugins/Plugins.scala:

    import sbt._
    class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
      val android = "android-plugin" % "android-plugin" % "1.3"
    }

and make the project definition in project/build/Project (for example):

    import sbt._
    import java.io.File

    class Project(info: ProjectInfo) extends AndroidProject(info) {
      // or preferably set the ANDROID_SDK_HOME environment variable
      override def androidSdkPath = Path.fromFile(new File("/home/mark/code/java/android-sdk-linux_x86-1.5_r2"))
    }


Alternatively, you can also use the script in contrib/ to set everything up for you:

    $ contrib/create_android_project --project MyAndroidProject --package com.foo.project

This will generate the project structure as well as all needed files plus skeleton tests, specs and activities.

To build the package:

    $ cd MyAndroidProject
    $ sbt update package-debug

##Credits

This code is based on work by Walter Chang
([saisiyat](http://github.com/weihsiu/saisiyat/)), turned into a plugin by
[Mark Harrah](http://github.com/harrah).
