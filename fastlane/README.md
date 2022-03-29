fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android test

```sh
[bundle exec] fastlane android test
```

Run all the tests

### android deploy

```sh
[bundle exec] fastlane android deploy
```

Deploy new version to Google Play and APK Store options: beta:false (default)

### android deployToPlaystore

```sh
[bundle exec] fastlane android deployToPlaystore
```

Deploy new version to Play Store

### android deployToServer

```sh
[bundle exec] fastlane android deployToServer
```

Deploy new version to server

### android deployToFDroid

```sh
[bundle exec] fastlane android deployToFDroid
```

Deploy new version to F-Droid

### android checkTrackingAddedInDependencyUsingIzzyScript

```sh
[bundle exec] fastlane android checkTrackingAddedInDependencyUsingIzzyScript
```

Check if tracking added in some dependency using Izzy's script

### android checkTrackingAddedInDependencyUsingExodus

```sh
[bundle exec] fastlane android checkTrackingAddedInDependencyUsingExodus
```

Check if tracking added in some dependency using exodus

### android createGitHubDraftRelease

```sh
[bundle exec] fastlane android createGitHubDraftRelease
```

Create GitHub draft release

### android dryRun

```sh
[bundle exec] fastlane android dryRun
```

Dry run - check tracking added for all flavors

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
