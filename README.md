![cryptomator-android](cryptomator-android.png)

[![Twitter](https://img.shields.io/badge/twitter-@Cryptomator-blue.svg?style=flat)](http://twitter.com/Cryptomator)
[![Community](https://img.shields.io/badge/help-Community-orange.svg)](https://community.cryptomator.org)
[![Documentation](https://img.shields.io/badge/help-Docs-orange.svg)](https://docs.cryptomator.org)
[![Crowdin](https://badges.crowdin.net/cryptomator-android/localized.svg)](https://crowdin.com/project/cryptomator-android)

Cryptomator offers multi-platform transparent client-side encryption of your files in the cloud.

Cryptomator for Android is currently available in the following  distribution channels:

1. [Using Google Play](https://play.google.com/store/apps/details?id=org.cryptomator)
2. [Using Cryptomator's Website](https://cryptomator.org/android/)
3. Building from source using Gradle (instructions below)

## Building

### Dependencies

* Git
* JDK 8
* Gradle

### Run Git and Gradle

```
git submodule init && git submodule update // (not necessary if cloned using --recurse-submodules)
./gradlew assembleLicenseDebug
```

Before connecting to Onedrive or Dropbox you have to enter valid API keys in [secrets.properties](https://github.com/cryptomator/android/blob/master/secrets.properties).

## License

This project is dual-licensed under the GPLv3 for FOSS projects as well as a commercial license for independent software vendors and resellers. If you want to modify this application under different conditions, feel free to contact our support team.
