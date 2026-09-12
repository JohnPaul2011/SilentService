# SilentService

A hidden Android background service template.

## Features
- Hidden from launcher (no app icon on home screen).
- WorkManager-based foreground service for battery efficiency.
- Auto-updates from GitHub Releases.
- Boot auto-start and resilient restart logic.

## Setup Instructions
1. Clone the repository.
2. Open the project in Android Studio.
3. Update `OWNER` and `REPO` constants in `UpdateChecker.kt` to match your GitHub repository.
4. Build and install the APK on your device.
5. Start the service via ADB since there is no launcher icon:
   ```bash
   adb shell am start -n com.jp1828.utils/.MainActivity
   ```
6. (Optional) Enable accessibility or device admin settings if your custom logic requires it.

## Adding Custom Logic
Locate `MainService.kt` and replace the `TODO("Add your logic here")` comment with your desired background task.

## Auto-Update
To trigger an auto-update:
1. Commit your changes and bump the version in `app/build.gradle.kts`.
2. Push a new tag starting with `v` (e.g., `v1.0.1`) to your GitHub repository.
3. GitHub Actions will automatically build the APK and create a release. The app will detect this on its next background check (every 6 hours) and prompt for installation.

## ADB Cheatsheet
- **Start Activity:** `adb shell am start -n com.jp1828.utils/.MainActivity`
- **Force Stop:** `adb shell am force-stop com.jp1828.utils`
- **Uninstall:** `adb uninstall com.jp1828.utils`
- **Check Logs:** `adb logcat -s UpdateChecker MainService`

[![Build and Release](https://github.com/OWNER/REPO/actions/workflows/build.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/build.yml)
