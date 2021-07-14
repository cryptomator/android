fastlane documentation
================
# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```
xcode-select --install
```

Install _fastlane_ using
```
[sudo] gem install fastlane -NV
```
or alternatively using `brew install fastlane`

# Available Actions
## Android
### android test
```
fastlane android test
```
Run all the tests
### android deploy
```
fastlane android deploy
```
Deploy new version to Google Play and APK Store options: beta:false (default)
### android deployToPlaystore
```
fastlane android deployToPlaystore
```
Deploy new version to Play Store
### android deployToServer
```
fastlane android deployToServer
```
Deploy new version to server
### android deployToFDroid
```
fastlane android deployToFDroid
```
Deploy new version to F-Droid
### android checkTrackingAddedInDependency
```
fastlane android checkTrackingAddedInDependency
```
Check if tracking added in some dependency
### android createGitHubDraftRelease
```
fastlane android createGitHubDraftRelease
```
Create GitHub draft release

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.
More information about fastlane can be found on [fastlane.tools](https://fastlane.tools).
The documentation of fastlane can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
