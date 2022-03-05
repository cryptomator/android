![cryptomator-android](cryptomator-android.png)

# Cryptomator for Android patched
## Removed license check and made the app working for free

### Building

Dependencies:
* Git
* JDK 11
* Gradle
* Android Studio

Run:
```bash
export ANDROID_SDK_ROOT=~/Android/Sdk
git clone --depth 1 https://github.com/andrigamerita/cryptomator-android-patched
cd cryptomator-android-patched
git submodule init && git submodule update
./gradlew assembleApkstoreRelease
jarsigner presentation/build/outputs/apk/apkstore/release/presentation-apkstore-release-unsigned.apk -keystore KEYSTORE_FILE KEY_ALIAS
```

Sign the APK and it is ready.

### Connecting to OneDrive/Dropbox

Support for OneDrive/Dropbox is not included in my builds since I don't use them.  
Before connecting to OneDrive or Dropbox you have to provide valid API keys using environment variables:   
For build type

* **release**: `DROPBOX_API_KEY` or `ONEDRIVE_API_KEY` and  `ONEDRIVE_API_REDIRCT_URI`
* **debug**: `DROPBOX_API_KEY_DEBUG` or `ONEDRIVE_API_KEY_DEBUG` and `ONEDRIVE_API_REDIRCT_URI_DEBUG`

## License

[Original repository](https://github.com/cryptomator/android)
This fork/patch is not affiliated with the original.

This project is dual-licensed under the GPLv3 for FOSS projects as well as a commercial license for independent software vendors and resellers. If you want to modify this application under different conditions, feel free to contact our support team.
