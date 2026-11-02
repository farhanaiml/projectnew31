# DeepScan – Android storage investigator

An Android 11+ (`minSdk 30`) **offline-first native Kotlin / Jetpack Compose** app with a dark dashboard and explicit file-cleanup controls.

> **Build status: source only in this environment.** The local container has no Android SDK, Android Gradle Plugin, Gradle, or Android emulator, and cannot access the network to fetch them. This zip is **not** a compiled or phone-tested APK. The GitHub Actions workflow is prepared but has **not been run**. Do not claim a successful Android build until CI confirms it.

## App capabilities

- Used / available device storage summary, clearly separated from scannable shared-file bytes.
- Recursive traversal of readable shared folders with exact paths and aggregate folder sizes.
- Largest files and folder tree with filename search, file details, copy path and external file viewer.
- Exact-file duplicate detection with SHA-256 (only hash same-size candidates).
- Optional *single-file* permanently deleting after explicit confirmation; **never auto-deletes**.
- Live scan stage, cancellation, error messaging, restricted-path tally and file cap.
- No login, internet access, analytics services, server or cloud processing.
- Explicit storage permission page and scope explanation.

## Build APK using GitHub Actions (Android Studio is NOT required)

1. Create a GitHub repository and upload the **contents of the DeepScan folder**, including its hidden `.github` folder, not the ZIP as a single file.
2. GitHub automatically runs `.github/workflows/build-android-apk.yml` on a push to `main` or `master`. Or open **Actions → DeepScan Android APK → Run workflow**.
3. Wait until all three jobs succeed: **Compile, unit tests, lint**, **Android emulator launch and interaction smoke tests**, and **Release APK only after build + emulator checks pass**. Then download the **DeepScan-tested-installable-APK** artifact. The workflow does not publish this final artifact until the emulator test succeeds.
4. Extract the downloaded artifact zip and copy `app-debug.apk` to your Android 11+ phone.
5. Open it on the phone and confirm installation. Because it is distributed outside the Play Store, Android may ask you to grant permission to install from that source. Only grant this if you trust the package you built.
6. Open DeepScan and turn on **All files access** when prompted, then tap **Start deep scan**.
7. Optionally inspect **Android emulator launch test** job to confirm its UI smoke test passed.

The workflow uses an official Android SDK setup action, Gradle 8.9, Android API 35, Android lint checks, JUnit unit tests, a Compose Android emulator UI smoke test, and publishes a **debug-signed installable APK** as an artifact **only if all those checks succeed**. The workflow itself has not yet been executed in this environment and may need updates if dependencies change.

## Development stack

- Android SDK 35, Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17, Kotlin 2.0.21
- Compose Material 3, Coroutines, AndroidX ViewModel, FileProvider
- Pure Kotlin `DiskScanEngine` for the core file investigation algorithm
- JUnit tests in `app/src/test`, Compose instrumentation smoke test in `app/src/androidTest`

## Local (non-Android) core test, already run successfully

```bash
./tools/test-core.sh
```

The pure JVM smoke harness tests file counts, nested folder sizes, SHA-256 duplicates, restricted folder exclusion, read-only scanning, cancellation, and formatting. This tests the **scanner logic** only, not Android permission behavior or phone UI.

## Important constraints and safety notes

- Shared storage is not the same as all device storage. Android forbids third-party apps from inspecting other apps' private internal data and protected `Android/data` or `Android/obb` locations, even with `MANAGE_EXTERNAL_STORAGE`.
- StorageStats and app private caches are not examined in this version. Other removable storage volumes are not scanned.
- On devices with exceptionally large files, SHA-256 hashing can take time. Scans are cancellable; a 200,000-file cap avoids uncontrolled memory growth.
- Scanned file sizes do not reconcile perfectly with Android's total occupied storage (partitions, cache, metadata and other apps' data differ).
- All deletion is **permanent and irreversible**; no automatic deletion or bulk delete. File viewers are opened through Android FileProvider.
- `MANAGE_EXTERNAL_STORAGE` is restricted for Google Play distribution. The build is intended for personal APK installation/testing, not automatic Play Store approval.
- Root privileges or bypassing Android security controls are neither requested nor attempted.
- Android 11 (API 30) or later is required. Android 10 and older are not supported in this implementation.

## Project layout

```
DeepScan/
  .github/workflows/build-android-apk.yml   GitHub cloud build and emulator test
  app/build.gradle.kts                       Android app + dependencies
  app/src/main/AndroidManifest.xml          Permissions, activity, FileProvider
  app/src/main/java/com/deepscan/app/
    MainActivity.kt                         UI, 4 navigation tabs, file details
    ScanViewModel.kt                        Scan state and guarded deletion
    StorageScanner.kt                       Android storage bridge
    DiskScanEngine.kt                       Pure Kotlin directory + duplicate scanner
  app/src/main/res/xml/file_paths.xml       Safe file viewer URI mappings
  app/src/test/.../DiskScanEngineTest.kt    JUnit core tests
  app/src/androidTest/.../AppLaunchTest.kt  Emulator UI launch smoke test
  tools/test-core.sh                        Local JVM test script
```

## Known limitations before shipping to other users

Compilation and emulator testing need a build runner. Additional real-phone testing should cover different Android versions and OEM permission interfaces, USB/SD storage, media viewer compatibility, symlinks, extremely large directories, permissions revoked during scanning, and deletion flows.
