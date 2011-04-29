##Usage

Requires [sbt](http://simple-build-tool.googlecode.com/) and the [Android SDK](http://developer.android.com/sdk/index.html) (`brew install sbt android-sdk` when using [homebrew](http://github.com/mxcl/homebrew) on OSX).

Using a [giter8][g8] template is the easiest way to create a new
project that uses the plugin. If you don't have giter8 installed:

[g8]: https://github.com/n8han/giter8#readme

    $ curl https://github.com/n8han/conscript/raw/master/setup.sh | sh
    $ ~/bin/cs n8han/giter8

Now create a new project with one of the Android templates:

    $ ~/bin/g8 n8han/android-app

This will prompt you to customize a few values (press enter to accept
defaults), then create the project structure and all needed files plus
skeleton tests, specs and activities.

To build the package:

    $ cd <your app name>
    $ sbt # enter sbt's interactive mode

    > update
    > package

To install and start the main activity in the [Android Emulator][emu]
(must already be running):

[emu]: http://developer.android.com/guide/developing/tools/emulator.html

    > start-emulator

To build a signed package for release into the Marketplace:

    > sign-release

##Scala Versions

The version of Scala that sbt compiles your project against is
configured in the `buildScalaVersion` property in the
`project/build.properties` file. You can set this to any Scala
version.

Whenever you change build versions, you'll need to run `update` again
to fetch dependencies. For more information, see the sbt documentation
on [cross-building][cb].

[cb]: http://code.google.com/p/simple-build-tool/wiki/CrossBuild

##Typed resources references

As an enhancement to the Android build process, this plugin can
generate typed references to application layout elements. To enable,
mix the `TypedResources` trait into your sbt project
definition. During compilation a file `TR.scala` will be generated
under `src_managed/main/scala`.

Typed resource references are created in an object `TR` (similar to
Android's standard `R`). These are handled by the method `findView`
defined in the traits `TypedView` and `TypedActivity`. There are also
implicit conversions defined in the object `TypedResource`; import
these to add the method on demand to any views and activities in
scope. The `findView` method casts the view to the known resource type
before returning it, so that application code can avoid the redundancy
of casting a resource to a type it has declared in the resource
definition.

Since Android's resource IDs are scoped to the application, a warning
is issued by the plugin when the same ID is used for different types
of a resources; the type of resources retrieved by that ID will be
unpredictable.

##Building Java Android projects with sbt

If you don't use Scala yet and want to use the plugin to build your existing
Java app you can do so by adding the `PlainJavaProject` trait to the project
definition:

    class MainProject(info: ProjectInfo) extends AndroidProject(info)
      with PlainJavaProject {
      // usual project configuration
    }

This will change the defaults to the directory structure expected by Android's
`build.xml` file and skip the Proguard optimisation step.

##Hacking on the plugin

If you need make modifications to the plugin itself, you can compile
and install it locally (you need at least sbt 0.7.x to build it):

    $ git clone git://github.com/jberkel/android-plugin.git
    $ cd android-plugin
    $ sbt publish-local    

Because the plugin gets cached in a project based on its version
number you might need to use `sbt clean-plugins` to force a reload
after `sbt publish-local`. Don't hesitate to send a pull request!

##Mailing list

There's no official mailing list for the project but most contributors hang
out in [scala-on-android][] or [simple-build-tool][].

##Credits

This code is based on work by Walter Chang
([saisiyat](http://github.com/weihsiu/saisiyat/)), turned into a plugin by
[Mark Harrah](http://github.com/harrah).


[scala-on-android]: http://groups.google.com/group/scala-on-android
[simple-build-tool]: http://groups.google.com/group/simple-build-tool
