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

## License

This project is dual-licensed under the GPLv3 for FOSS projects as well as a commercial license for independent software vendors and resellers. If you want to modify this application under different conditions, feel free to contact our support team.
