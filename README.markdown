##Usage

Requires [sbt](http://simple-build-tool.googlecode.com/) and the [Android SDK](http://developer.android.com/sdk/index.html) (`brew install sbt android-sdk` when using [homebrew](http://github.com/mxcl/homebrew) on OSX).

To use the plugin in a project, you just need to create project/plugins/Plugins.scala:

    import sbt._
    class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
      val android = "org.scala-tools.sbt" % "sbt-android-plugin" % "0.4.2"
    }

and make the project definition in project/build/Project (for example):

    import sbt._
    import java.io.File

    class Project(info: ProjectInfo) extends AndroidProject(info) with MarketPublish {
      override def androidPlatformName = "android-2.1"

      // or preferably set the ANDROID_SDK_HOME environment variable
      override def androidSdkPath = Path.fromFile(new File("/usr/local/Cellar/android-sdk/r5"))

      // set to the keystore alias you used when creating your keychain
      val keyalias = "my_keys"

      // set to the location of your keystore
      override def keystorePath = Path.userHome / ".keystore" / "mykeys.keystore"
    }


Alternatively, you can also use a provided script to set everything up for you:

    $ script/create_project myAndroidProject com.foo.project

This will generate the project structure as well as all needed files plus skeleton tests, specs and activities.

To build the package:

    $ cd myAndroidProject
    $ sbt update package-debug

To install the package:

    $ sbt install-emulator

To install and automatically start the main activity

    $ sbt start-emulator

To build a signed package for release into the Marketplace

    $ sbt sign-release

##Hacking on the plugin

If you need make modifications to the plugin itself, you can compile and install it locally (you need at least sbt 0.7.x to build it):

    $ git clone git://github.com/jberkel/android-plugin.git
    $ cd android-plugin
    $ sbt publish-local    

Because the plugin gets cached in a project based on its version number you might need to use `sbt clean-plugins` to force a reload after `sbt publish-local`.

##Credits

This code is based on work by Walter Chang
([saisiyat](http://github.com/weihsiu/saisiyat/)), turned into a plugin by
[Mark Harrah](http://github.com/harrah).
