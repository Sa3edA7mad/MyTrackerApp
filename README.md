# MyTrackerApp

An Android app for tracking a 4-week workout program, built with Jetpack Compose, Room, and Navigation.

## Requirements

- Android Studio (a recent version that bundles JDK 21, e.g. Narwhal or newer)
- JDK 21 (used as the Gradle toolchain; app code targets Java 17 bytecode)
- Android SDK Platform 37 installed (compileSdk / targetSdk = 37)

## Getting started

1. Clone the repo:
   ```bash
   git clone https://github.com/Sa3edA7mad/MyTrackerApp.git
   ```
2. Open the project folder in Android Studio and let it sync Gradle.
3. Run the `app` configuration on an emulator or device with **API 26+**.

### Command line

```bash
./gradlew assembleDebug
```

Run unit tests:

```bash
./gradlew test
```

Run instrumented tests (needs a connected device/emulator):

```bash
./gradlew connectedAndroidTest
```

## Tech stack

- Kotlin 2.2.10, AGP 9.2.1 (AGP's built-in Kotlin — no separate `org.jetbrains.kotlin.android` plugin)
- Jetpack Compose (BOM 2026.02.01) + Material 3
- Room 2.8.5 (via KSP) for persistence
- Navigation Compose for screen routing
- DataStore Preferences for settings

## Known toolchain quirks

- **No Compose icon library is available** on this BOM — icons are drawn as local vector drawables in `app/src/main/res/drawable/ic_*.xml` instead of `androidx.compose.material.icons`.
- **KSP requires `android.disallowKotlinSourceSets=false`** in `gradle.properties` (already set) because AGP 9's built-in Kotlin otherwise rejects the source sets KSP registers for Room's generated code.

## Project structure

```
app/src/main/java/com/example/mytrackerapp/
├── data/        # Room database, DAOs, entities, DataStore-backed settings, seed data
├── di/          # Simple manual dependency container
├── domain/      # Program/model logic independent of Android framework
├── repo/        # Repository layer bridging data and UI
└── ui/          # Compose screens, navigation, theme, shared components
```
