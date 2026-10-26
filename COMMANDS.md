# Development guide

Commands to build, install, test and debug TUI-VibeLauncher. For the features, see [README.md](README.md).

## Requirements

- JDK 17 or later
- Android SDK with platform 35 and build tools (Android Studio installs them, or use `sdkmanager`)
- `adb`, from the SDK `platform-tools` folder

The SDK location goes in `local.properties`:

```
sdk.dir=/path/to/Android/Sdk
```

On Windows, use `gradlew.bat` instead of `./gradlew` in the commands below.

## Building

```bash
# Debug build: not shrunk, signed with the debug key
./gradlew assembleFdroidDebug

# Release build: shrunk and obfuscated by R8
./gradlew assembleFdroidRelease
```

| Build | Output |
|---|---|
| Debug | `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk` |
| Release | `app/build/outputs/apk/fdroid/release/app-fdroid-release.apk` |

The `fdroid` flavor includes the SMS permissions. A `playstore` flavor without them also exists (`assemblePlaystoreRelease`).

## Signing

The release build reads its signing key from `local.properties`, which is not versioned:

```
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`storeFile` is relative to the project root. Without these lines, the release build is signed with the debug key.

Android installs an update only if it is signed with the same key as the installed app. Keep a copy of the keystore and its passwords: without them, the next version can only be installed after uninstalling the app, which deletes its settings (run `tui -backup` first).

To create a keystore:

```bash
keytool -genkeypair -v -keystore release.jks -alias tui -keyalg RSA -keysize 4096 -validity 10000
```

## Emulator

```bash
# Install the emulator and an Android 15 image
sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"

# Create a virtual device
avdmanager create avd -n tui_test -k "system-images;android-35;google_apis;x86_64" -d pixel_7

# Start it
emulator -avd tui_test -no-boot-anim -no-audio
```

The emulator needs about 7 GB of free space for its data partition. If the drive holding the user folder is short on space, create the device elsewhere with `-p /other/path/tui_test.avd`, or lower `disk.dataPartition.size` in the device's `config.ini`.

From the emulator, the host machine is reachable at `10.0.2.2`.

## Installing

```bash
# Wait for the device, then install over the existing app (settings are kept)
adb wait-for-device
adb install -r app/build/outputs/apk/fdroid/release/app-fdroid-release.apk

# Make T-UI the home app
adb shell cmd package set-home-activity ohi.andre.consolelauncher/.LauncherActivity
adb shell input keyevent KEYCODE_HOME
```

`install -r` fails if the APK is signed with a different key than the installed app. In that case, uninstall first (this deletes the settings).

## Permissions for testing

These commands grant permissions without going through the settings screens. Use them on a test device or emulator.

```bash
# All files access (needed for ls, cat... outside the app folder)
adb shell appops set ohi.andre.consolelauncher MANAGE_EXTERNAL_STORAGE allow

# Notification access (for notif)
adb shell cmd notification allow_listener ohi.andre.consolelauncher/ohi.andre.consolelauncher.managers.notifications.NotificationService

# Post a test notification
adb shell cmd notification post -t "Title" test_tag "Text"
```

## Driving T-UI from adb

```bash
# Type a command and run it (%s is a space)
adb shell input text "ping%s-c%s3%s127.0.0.1"
adb shell input keyevent KEYCODE_ENTER

# Quote the text when it contains | > ; or & (otherwise the device shell interprets them)
adb shell "input text 'scan%s|%sgrep%s10.0'"

# Tab completion and long press (x y x y duration in ms)
adb shell input keyevent KEYCODE_TAB
adb shell input swipe 300 1800 300 1800 1500

# Screenshot
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png
```

On Windows, don't redirect `adb exec-out screencap` to a file in PowerShell: the redirection corrupts the image. Use `screencap` then `adb pull` as above.

To test the boot screen or the startup script, start T-UI from scratch:

```bash
adb shell am force-stop ohi.andre.consolelauncher
adb shell input keyevent KEYCODE_HOME
```

## Files

T-UI keeps its files in the app's external folder. Android deletes it when the app is uninstalled.

```
/sdcard/Android/data/ohi.andre.consolelauncher/files/
    behavior.xml, ui.xml, theme.xml...   settings
    alias.txt                            aliases
    history.txt                          command history
    .tuirc                               startup script
    ssh/hosts.txt                        saved servers
    ssh/known_hosts                      server fingerprints
    ssh/id_ed25519, ssh/id_ed25519.pub   SSH key pair
```

```bash
# List the files
adb shell ls -la /sdcard/Android/data/ohi.andre.consolelauncher/files/

# Copy a file to or from the device
adb push .tuirc /sdcard/Android/data/ohi.andre.consolelauncher/files/
adb pull /sdcard/Android/data/ohi.andre.consolelauncher/files/history.txt
```

## Debugging

```bash
# T-UI logs
adb logcat | grep ohi.andre

# Crashes only
adb logcat -b crash

# Running processes started by T-UI's shell
adb shell ps -A -o PID,PPID,NAME

# Uninstall (deletes the settings)
adb uninstall ohi.andre.consolelauncher
```

## Release checks

R8 renames classes in the release build. T-UI finds its commands by class name and JSch loads its algorithms by class name, so `app/proguard-rules.pro` keeps:

- `ohi.andre.consolelauncher.commands.main.raw.**`, the commands;
- `com.jcraft.jsch.**`, the SSH library.

A new command must be in `commands/main/raw` to be found. After changing the rules, test SSH and a few commands on the release build, not only on the debug build.
