#Usage

Requires [sbt](http://simple-build-tool.googlecode.com/) and the
[Android SDK](http://developer.android.com/sdk/index.html)
(`brew install sbt android-sdk` when using [homebrew](http://github.com/mxcl/homebrew) on OSX).

For those who are familiar with the 0.7.x plugin, there is a
[migration guide](https://github.com/philcali/android-plugin/blob/master/migration_guide.md)
for a quick reference.

Using a [giter8][g8] template is the easiest way to create a new
project that uses the plugin. If you don't have giter8 installed:

[g8]: https://github.com/n8han/giter8#readme

    $ curl https://raw.github.com/n8han/conscript/master/setup.sh | sh
    $ ~/bin/cs n8han/giter8

Now create a new project with one of the Android templates:

    $ ~/bin/g8 philcali/android-app

This will prompt you to customize a few values (press enter to accept
defaults), then create the project structure and all needed files plus
skeleton tests, specs and activities.

To build the package:

    $ cd <your app name>
    $ export ANDROID_HOME=/path/to/sdk # or ANDROID_SDK_{HOME,ROOT}
    $ sbt # enter sbt's interactive mode

    > update
    > package

To install and start the main activity in the [Android Emulator][emu]
(must already be running):

[emu]: http://developer.android.com/guide/developing/tools/emulator.html

    > start-emulator

To build a signed package for release into the Marketplace:

    > prepare-market

##Launching the emulator from sbt

A developer can now fire up the Android Emulator from the sbt terminal:

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
on [cross-building][cb].

[cb]: https://github.com/harrah/xsbt/wiki/Cross-Build

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

    > sbt screenshot-emulator

or

    > sbt screenshot-device

The screenshots will be written to `emulator.png` / `device.png` in the project
root directory.

##Hacking on the plugin

If you need make modifications to the plugin itself, you can compile
and install it locally (you need at least sbt 0.10.x to build it):

    $ git clone git://github.com/philcali/android-plugin.git
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
[Mark Harrah](http://github.com/harrah), and maintained by
[Jan Berkel](https://github.com/jberkel).


[scala-on-android]: http://groups.google.com/group/scala-on-android
[simple-build-tool]: http://groups.google.com/group/simple-build-tool
