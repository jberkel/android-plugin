#Usage

Requires [sbt][] and the [Android SDK][]
(`brew install sbt android-sdk` when using [homebrew][] on OSX).

For those who are familiar with the 0.7.x plugin, there is a [migration guide][]
for a quick reference. The 0.7.x version is no longer maintained - but it is
still available in the [0.7.x][] branch.

Using a [giter8][] template is the easiest way to create a new
project that uses the plugin. If you don't have giter8 installed:


    $ curl https://raw.github.com/n8han/conscript/master/setup.sh | sh
    $ ~/bin/cs n8han/giter8

Now create a new project with one of the Android templates:

    $ ~/bin/g8 jberkel/android-app

This will prompt you to customize a few values (press enter to accept
defaults), then create the project structure and all needed files plus
skeleton tests, specs and activities.

To build the package:

    $ cd <your app name>
    $ export ANDROID_HOME=/path/to/sdk # or ANDROID_SDK_{HOME,ROOT}
    $ sbt # enter sbt's interactive mode

    > android:package-debug

To install and start the main activity in the [Android Emulator][]
(must already be running):

    > android:start-emulator

To build a signed package for release into the Marketplace:

    > android:prepare-market

##Launching the emulator from sbt

A developer can now fire up the Android Emulator from the sbt terminal
(hint: you can get a list of all avds with tab completion)

    > android:emulator-start <my_avd>

To list all devices or emulators

    > android:list-devices

To stop the emulator:

    > android:emulator-stop

##Scala Versions

The version of Scala that sbt compiles your project against is
configured in the `scalaVersion` property in the
`project/build.scala` file. You can set this to any Scala
version.

Whenever you change build versions, you'll need to run `update` again
to fetch dependencies. For more information, see the sbt documentation
on [cross-building][].


##Android manifest files

If you would like your AndroidManifest.xml file to automatically inherit
versionName and versionCode from your SBT project, add the
`AndroidManifestGenerator.settings` build settings to your project.
It will look for an AndroidManifest.xml file, and add versionName
and versionCode to that template.

##Typed resources references

As an enhancement to the Android build process, this plugin can
generate typed references to application layout elements. To enable,
add the `TypedResources.settings` build settings into your sbt project
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

## Getting screenshots

In the sbt console run:

    > android:screenshot-emulator

or

    > android:screenshot-device

The screenshots will be written to `emulator.png` / `device.png` in the project
root directory.

## Fetch hprof memory dumps

    > android:hprof-emulator
    > android:hprof-device

##Building Java Android projects with sbt

If you don't use Scala yet and want to use the plugin to build your existing
Java app you can do by adding the `PlainJavaProject.settings` to your settings:

```scala
object AndroidBuild extends Build {
  lazy val main = Project (
    "My Project",
    file("."),
    settings = General.fullAndroidSettings ++ PlainJavaProject.settings
  )
}
```

This will change the defaults to the directory structure expected by Android's
`build.xml` file and skip the Proguard optimisation step.

##Building Android NDK projects

There is some basic NDK support in the android-plugin.
For now, it doesn't do anything else than call ndk-build during compilation
and clean up the obj and libs directories during cleanup.
This depends on an environment variable being set up: either `ANDROID_NDK_HOME` or `ANDROID_NDK_ROOT`.

Place your Android NDK sources in `src\main\jni`. Add the AndroidNdk.settings to your project:

```scala
  lazy val someProjectUsingNDK = Project(
    id = ...,
    ...
    settings = ... ++ AndroidBase.settings ++ AndroidNdk.settings
  )
```

##Hacking on the plugin

If you need make modifications to the plugin itself, you can compile
and install it locally (you need at least sbt 0.10.x to build it):

    $ git clone git://github.com/jberkel/android-plugin.git
    $ cd android-plugin
    $ sbt publish-local

##Mailing list

There's no official mailing list for the project but most contributors hang
out in [scala-on-android][] or [simple-build-tool][].

##Credits

This code is based on work by Walter Chang
([saisiyat](http://github.com/weihsiu/saisiyat/)), turned into a plugin by
[Mark Harrah](http://github.com/harrah), and maintained by
[Jan Berkel](https://github.com/jberkel).

A lot of people have contributed to the plugin; see [contributors][] for a full
list.

[sbt]: https://github.com/harrah/xsbt/wiki
[scala-on-android]: http://groups.google.com/group/scala-on-android
[simple-build-tool]: http://groups.google.com/group/simple-build-tool
[0.7.x]: https://github.com/jberkel/android-plugin/tree/0.7.x
[migration guide]: https://github.com/jberkel/android-plugin/wiki/migration_guide
[contributors]: https://github.com/jberkel/android-plugin/wiki/Contributors
[homebrew]: https://github.com/mxcl/homebrew
[giter8]: https://github.com/n8han/giter8#readme
[Android SDK]: http://developer.android.com/sdk/index.html
[Android emulator]: http://developer.android.com/guide/developing/tools/emulator.html
[cross-building]: https://github.com/harrah/xsbt/wiki/Cross-Build
