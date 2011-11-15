#Usage

Requires [sbt][] (0.11.1+) and the [Android SDK][]
(`brew install sbt android-sdk` when using [homebrew][] on OSX).

For those who are familiar with the 0.7.x plugin, there is a [migration guide][]
for a quick reference. The 0.7.x version is no longer maintained - but it is
still available in the [0.7.x][] branch.

## Getting started

See the [Getting started][] guide on the wiki for more documentation.

## Hacking on the plugin

If you need make modifications to the plugin itself, you can compile
and install it locally (you need at least sbt 0.11.x to build it):

    $ git clone git://github.com/jberkel/android-plugin.git
    $ cd android-plugin
    $ sbt publish-local

##Mailing list

There's no official mailing list for the project but most contributors hang
out in [scala-on-android][] or [simple-build-tool][].

You can also check out a list of
[projects using sbt-android-plugin][] to see some real-world examples.

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
[Android SDK]: http://developer.android.com/sdk/index.html
[projects using sbt-android-plugin]: https://github.com/jberkel/android-plugin/wiki/Projects-using-sbt-android-plugin
[Getting started]: https://github.com/jberkel/android-plugin/wiki/getting-started
