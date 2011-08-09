# Migration Guide

Upgrading the plugin was not a 1:1 match unfortunately. While most of the logic
is the same, some the build object had to be renamed significantly. Names that
made sense for definition mixin's are now objects.

## Quick Reference

For those of you who are familiar with the android plugin's previous definition,
here's a quick match to the important things:

`AndroidProject` => `AndroidProject.androidSettings`
`BaseAndroidProject` => `AndroidBase.settings`
`AndroidLibraryProject` => `AndroidBase.settings`
`DdmSupport` => `AndroidDdm.settings`
`Startable` => `AndroidLaunch.settings`
`Installable` => `AndroidInstall.settings`
`TypedResources` => `TypedResources.settings`
`MarketPublish` => `AndroidMarketPublish.settings`
`AndroidManifestGenerator` => `AndroidManifestGenerator.settings`

## Overriding settings

All settings and tasks are placed in the `Android` config, so be sure to
override the settings in the appropriate scope. For example, to override the 
`skipProguard` setting you must do it the following way:

```scala
import AndroidKeys._

skipProguard in Android := true
```

Every setting and task used in the plugin can be found in `AndroidKeys`, akin to 
`sbt.Keys._`

Definitions that weren't ported over: `PlainJavaProject`.
