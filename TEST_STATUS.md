# DeepScan — factual verification status

Date: 29 October 2026

## Verified in this workspace

- `tools/test-core.sh`: PASS; compiles the pure Kotlin scan engine and runs a JVM smoke harness with sample files.
- Android resource XML files: parsed without syntax errors.
- GitHub Actions workflow YAML: parsed without syntax errors.
- Final source ZIP: tested for archive integrity.

The JVM smoke harness exercises nested folder accounting, SHA-256 identical-content detection, protected Android-directory exclusion, no deletion on scan, cancellation and human-readable file size formatting.

## Not verified in this workspace

- Android Gradle Plugin compilation and APK packaging: not run.
- Android lint task and JUnit-in-Gradle unit tests: not run.
- Android emulator UI instrumentation: not run.
- Actual phone install/scan/permissions/deletion testing: not run.

## Why

The workspace has Java/Kotlin, but no Android SDK, Android Gradle Plugin, Gradle binary or Android emulator. Network DNS lookups for `dl.google.com`, `services.gradle.org` and Maven repository hosts fail. The environment cannot fetch build dependencies.

## Cloud-build path

The GitHub Actions configuration at `.github/workflows/build-android-apk.yml` is set to install the toolchain, compile, lint, unit test and run the app launch instrumentation test in an Android API 35 emulator. It publishes a downloadable `DeepScan-tested-installable-APK` artifact **only after every job passes**. No such CI run has been initiated or verified yet. The user must grant repository access to trigger this build.

## Notes

Even a passing emulator launch test cannot prove that the device-dependent deep-storage scan works on every Android phone. A real-phone permissions/scanning/deletion test remains necessary before declaring a stable production release. Android-protected folders remain inaccessible without elevated privileges.
