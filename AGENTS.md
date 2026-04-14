# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

Single-module native Android gallery app (Kotlin + Jetpack Compose + Material 3). No backend services, no Docker, no web layer. All data is on-device (Room/SQLite, MediaStore, DataStore, Android Keystore).

### Environment

- **JDK 17** is required (`sourceCompatibility = JavaVersion.VERSION_17`). The system may have JDK 21 as default; ensure `JAVA_HOME` points to JDK 17.
- **Android SDK** is installed at `/opt/android-sdk` with platform `android-35` and `build-tools;35.0.0`.
- Environment variables are set in `~/.bashrc`:
  ```
  export ANDROID_HOME=/opt/android-sdk
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
  ```
- `local.properties` (gitignored) must contain `sdk.dir=/opt/android-sdk`. The update script creates it automatically.

### Build / Test / Lint

All commands use the Gradle wrapper from the repo root. Set `JAVA_HOME` and `ANDROID_HOME` before running, or source `~/.bashrc`.

| Task | Command |
|------|---------|
| Build debug APK | `./gradlew assembleDebug` |
| Unit tests | `./gradlew testDebugUnitTest` |
| Lint | `./gradlew lintDebug` |
| Build release APK | `./gradlew assembleRelease` |

### Gotchas

- **Lint has pre-existing errors**: `lintDebug` exits with code 1 due to 2 pre-existing errors (e.g., `FlowOperatorInvokedInComposition` in `MainActivity.kt`). This is a known issue in the codebase, not caused by agent changes.
- **No emulator in Cloud VMs**: Instrumented tests (`connectedAndroidTest`) and `installDebug` require a physical device or emulator, which is not available in headless Cloud Agent VMs. Unit tests and the debug APK build are the primary validation methods.
- **Gradle configuration cache** is enabled; if you change build scripts, you may need `--no-configuration-cache` on the next build or delete `.gradle/configuration-cache`.
- **JDK 21 vs 17**: The VM ships with JDK 21. AGP 8.7.3 + Kotlin 2.0.21 target JVM 17. Always ensure `JAVA_HOME` points to JDK 17.
