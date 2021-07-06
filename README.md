![cryptomator-android](cryptomator-android.png)

[![Twitter](https://img.shields.io/badge/twitter-@Cryptomator-blue.svg?style=flat)](http://twitter.com/Cryptomator)
[![Community](https://img.shields.io/badge/help-Community-orange.svg)](https://community.cryptomator.org)
[![Documentation](https://img.shields.io/badge/help-Docs-orange.svg)](https://docs.cryptomator.org)
[![Crowdin](https://badges.crowdin.net/cryptomator/localized.svg)](https://translate.cryptomator.org/)

Cryptomator offers multi-platform transparent client-side encryption of your files in the cloud.

Cryptomator for Android is currently available in the following  distribution channels:

1. [Using Google Play](https://play.google.com/store/apps/details?id=org.cryptomator)
2. [Using Cryptomator's Website](https://cryptomator.org/android/)
3. [Using Cryptomator's F-Droid Repository](https://cryptomator.org/android/)
4. Building from source using Gradle (instructions below)

## Building

### Dependencies

* Git
* JDK 11
* Gradle

### Run Git and Gradle

```
git submodule init && git submodule update // (not necessary if cloned using --recurse-submodules)
./gradlew assembleApkstoreDebug
```

Before connecting to OneDrive or Dropbox you have to provide valid API keys using environment variables:   
For build type

* **release**: `DROPBOX_API_KEY` or `ONEDRIVE_API_KEY` and  `ONEDRIVE_API_REDIRCT_URI`
* **debug**: `DROPBOX_API_KEY_DEBUG` or `ONEDRIVE_API_KEY_DEBUG` and `ONEDRIVE_API_REDIRCT_URI_DEBUG`

## Contributing to Cryptomator for Android

Please read our [contribution guide](.github/CONTRIBUTING.md), if you would like to report a bug, ask a question, translate the app or help us with coding.

Please make sure before creating a PR, to apply the code style by executing reformat code with optimize imports and rearrange code enabled. The best way to do this is to create a macro for it in android studio and set it to the save shortcut.

## Code of Conduct

Help us keep Cryptomator open and inclusive. Please read and follow our [Code of Conduct](.github/CODE_OF_CONDUCT.md).

## Deployment

Follow these steps to deploy a release:

1. Check `TODO`/`FIXME` comments
   - Create issue for or delete
   - Regexp for "Find in Path": `\W(TODO|FIXME)(?! #[0-9]{1,4}:)`
1. Merge translations
1. Check latest dependencies
1. Create release branch
1. Test database migration
1. Smoke-Test changed or added functionality
1. Update version
1. Create and commit release notes
1. Merge in `main`
1. Create tag and execute deploy app using Fastlane
1. Close GitHub-issues or move them to next milestone
1. Close milestone
1. Update version on website ([cryptomator.org/android](https://cryptomator.org/android/))

### Release Notes

Before tagging the release, create and commit the release notes. For Playstore create [fastlane/metadata/android/de-DE/changelogs/default.txt](https://github.com/cryptomator/android/blob/develop/fastlane/metadata/android/de-DE/changelogs/default.txt), [fastlane/metadata/android/en-US/changelogs/default.txt](https://github.com/cryptomator/android/blob/develop/fastlane/metadata/android/en-US/changelogs/default.txt) and for the website create [fastlane/release-notes.html](https://github.com/cryptomator/android/blob/develop/fastlane/release-notes.html).

### Deploy app using Fastlane

Deploy production version to Google Play, Website/GitHub-Releases and F-Droid using `fastlane android deploy` or `bundle exec fastlane deploy`

There are further targets and options like `beta`, see [fastlane/README.md](https://github.com/cryptomator/android/blob/develop/fastlane/README.md)

### Initial setup Fastlane

1. Make sure you copied `.default.env` to `.env` in the `fastlane` folder and filled out those variables.
1. Install Ruby (depends on OS, Ubuntu): `sudo apt install ruby-dev`
1. Install fastlane (depends on OS, Ubuntu): `gem install fastlane -N`
1. Install `fdroidserver` using `apt`, `pacman`, ..., see https://f-droid.org/docs/Installing_the_Server_and_Repo_Tools/

## License

This project is dual-licensed under the GPLv3 for FOSS projects as well as a commercial license for independent software vendors and resellers. If you want to modify this application under different conditions, feel free to contact our support team.
