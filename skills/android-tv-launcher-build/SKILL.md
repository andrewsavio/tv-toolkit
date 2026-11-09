---
name: android-tv-launcher-build
description: Build, sign and install an Android TV app without Gradle, using only the Android SDK command-line tools (aapt2, javac, d8, zipalign, apksigner). Covers the d8 and zipalign traps found on Android 11, and how to make a launcher the TV's home app. Use when the user wants to build an Android app with no Gradle, get "d8 null pointer" or "resources.arsc" errors, or install a custom home screen.
---

# Building an Android TV app without Gradle

## Prerequisites

- JDK 17 or newer on PATH.
- Android SDK with `build-tools;34.0.0` and `platforms;android-34`.
- A keystore for signing. `keytool -genkeypair` with alias `androiddebugkey` and password `android` is enough for side-loading.

## Build steps

```text
aapt2 compile --dir res -o build/res.zip
aapt2 link -I android.jar --manifest AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 34 -o build/unsigned.apk build/res.zip
javac --release 8 -classpath android.jar -d build/classes $(find src -name '*.java')
d8 --release --min-api 26 --lib android.jar --output build <class files>
jar uf build/unsigned.apk -C build classes.dex
zipalign -f -p 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out build/app.apk build/aligned.apk
adb install -r build/app.apk
```

`launcher/build.ps1` in the toolkit does exactly this on Windows.

## Traps

- **`d8` null pointer exception** while converting a class: a nested or anonymous class that uses generics (a `Comparator`, for example) triggered the bug in d8 8.2.2. Replace it with a top-level static class or a plain method.
- **Android 11 refuses to install or warns about `resources.arsc`:** zipalign must use `-p 4`. Without `-p`, the resources are not page-aligned.
- **`aapt2 link` fails on a new resource:** compile the resources first (`aapt2 compile`), then link the output.
- **`javac` warns about source 8:** harmless for this flow; the target is Android's dex format.
- **Lambdas:** they compiled fine in the launcher, but if d8 fails on one, rewrite it as a named class.

## Making it the home app

1. Declare `android.intent.action.MAIN` with `android.intent.category.HOME` and `DEFAULT` on the activity.
2. Declare `LEANBACK_LAUNCHER` too, so the TV lists it as a TV app.
3. Install it, then press Home on the remote. Choose the new app and tick *Always*, or let the TV ask.

## Updates

Installing an update can detach any accessibility service the app owns. Tell the user to re-enable it. The launcher's data (its settings) is kept on `install -r`.

## Checks

- `adb shell dumpsys package <pkg> | grep -E "versionName|enabled"` confirms the install.
- `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER` lists launcher entries.
