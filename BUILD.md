# Building without Android Studio

This project has no dependency on the Android Studio IDE - it's a
plain Gradle project. Two ways to get an installable APK:

## Option A - GitHub Actions (recommended, nothing to install)

1. Create a new empty GitHub repository.
2. Upload this whole `sms-bomber-guard` folder to it (drag-and-drop
   on github.com works fine, or `git push` if you're comfortable
   with git).
3. Go to the repo's **Actions** tab. The included workflow
   (`.github/workflows/build.yml`) runs automatically on push, or
   click **Run workflow** to trigger it manually.
4. When it finishes (a few minutes), open the run and download the
   **sms-bomber-guard-debug-apk** artifact - that's your APK.
5. Transfer the `.apk` to your phone (email, cloud drive, USB) and
   tap it to install. You'll need to allow "install from unknown
   sources" for whatever app you used to open it.

No SDK, no Gradle, no Java - all of it runs on GitHub's servers.

## Option B - your own computer's terminal (no Android Studio GUI)

1. Install a JDK 17 (e.g. `sudo apt install openjdk-17-jdk` on
   Linux, or download Temurin 17 for Mac/Windows).
2. Install Gradle itself (not the IDE) - e.g. via
   [sdkman](https://sdkman.io) (`sdk install gradle 8.6`), Homebrew
   (`brew install gradle`), or the manual zip from
   gradle.org/releases.
3. Install just the Android **command-line SDK tools** (not the full
   Android Studio app) from
   https://developer.android.com/studio#command-line-tools-only,
   unzip them, then run:
   ```
   sdkmanager --sdk_root=$HOME/android-sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```
4. Point Gradle at that SDK - create a file named `local.properties`
   in this project's root folder containing:
   ```
   sdk.dir=/absolute/path/to/android-sdk
   ```
5. From the project root, run:
   ```
   gradle assembleDebug
   ```
6. The APK appears at `app/build/outputs/apk/debug/app-debug.apk`.
   Copy it to your phone and install it, or connect the phone over
   USB with debugging enabled and run `adb install app/build/outputs/apk/debug/app-debug.apk`.

Either option gives you the exact same APK - Option A just does all
of step 1-5 for you in the cloud.

## Changing the app name

Edit `app/src/main/res/values/strings.xml` and change the
`app_name` value. That's the only place it's defined - the manifest
already points at it.

## Changing the app icon

The launcher icon is already set to your uploaded logo, exported as
PNGs at every required density:

- `app/src/main/res/mipmap-mdpi/ic_launcher.png` (and `_round.png`)
- `app/src/main/res/mipmap-hdpi/...`
- `app/src/main/res/mipmap-xhdpi/...`
- `app/src/main/res/mipmap-xxhdpi/...`
- `app/src/main/res/mipmap-xxxhdpi/...`

Nothing else to do - it builds as-is.

**To swap in a different image later:** generate a new set of
`mipmap-*/ic_launcher.png` (+ `_round.png`) files - e.g. with
https://icon.kitchen or
https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html -
and replace the files above with the new ones, keeping the same
folder/file names. Commit and push; the next Actions run picks it up.
