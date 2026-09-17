# MyTrackerApp

A native Android tracker for a personal 4-week bodyweight + resistance-band program,
built with Jetpack Compose, Room, and Navigation Compose. Offline-only — no accounts,
no network calls.

The program: 4 weeks, 6 training days a week, the same 13 exercises every week, with the
number of circuits per day ramping 4 → 5 → 6 → 7 across the weeks. Every day also gets a
warm-up before the first circuit and a stretch routine after the last.

## Features

- **Today** — the current day's circuits, warm-up, and stretch, with live progress.
- **Guided circuit mode** — one exercise at a time, with an auto-advancing hold timer
  (audio cues at 3-2-1-0, since the phone is usually out of easy reach mid-hold) for
  timed exercises, and a two-stage side-1/side-2 flow for per-side exercises.
- **Checklist mode** — the same circuit as a flat, tickable list. Switching modes
  mid-circuit preserves progress and resumes at the right exercise either way.
- **Program** — all 4 weeks at a glance; past days are reviewable, future days are a
  locked preview until the current day is finished.
- **Progress** — streak, cycle completion %, a 4×6 heat map of the whole cycle, and a
  "most done" ranking.
- **Library** — all 29 exercises (13 program + 8 warm-up + 8 stretch), searchable by
  name or muscle group, each with instructions, target, and a form-video link.
- **Cycle completion** — a summary screen once all 24 days are settled, with the option
  to start a fresh cycle without losing history.
- **Settings** — guided/checklist default, timer auto-advance, keep-screen-on, sound
  cues, haptics, JSON export of all training history, and a reset for the current cycle
  (past cycles are never touched).

## Requirements

- Android Studio (a recent version that bundles JDK 21, e.g. Narwhal or newer)
- JDK 21 (used as the Gradle toolchain; app code targets Java 17 bytecode)
- Android SDK Platform 37 installed (compileSdk / targetSdk = 37, minSdk = 26)

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

Run unit tests (74 tests — pure domain logic, no Android dependency):

```bash
./gradlew testDebugUnitTest
```

Run instrumented tests (53 tests — Room + repository behavior; needs a connected
device/emulator):

```bash
./gradlew connectedDebugAndroidTest
```

Both suites currently pass at 127/127. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#testing)
for what each suite actually guards and why.

## Tech stack

- Kotlin 2.2.10, AGP 9.2.1 (AGP's built-in Kotlin — no separate `org.jetbrains.kotlin.android` plugin)
- Jetpack Compose (BOM 2026.02.01) + Material 3
- Room 2.8.5 (via KSP) for persistence
- Navigation Compose for screen routing
- DataStore Preferences for settings

## Known toolchain quirks

- **No Compose icon library is available** on this BOM — icons are drawn as local vector drawables in `app/src/main/res/drawable/ic_*.xml` instead of `androidx.compose.material.icons`.
- **KSP requires `android.disallowKotlinSourceSets=false`** in `gradle.properties` (already set) because AGP 9's built-in Kotlin otherwise rejects the source sets KSP registers for Room's generated code.
- **Avoid `rememberUpdatedState(::localFunction)`** — a callable reference to a local
  function compares equal across recompositions, so the backing state silently never
  updates. See `docs/ARCHITECTURE.md` for the bug this caused and the fix.

## Project structure

```
app/src/main/java/com/example/mytrackerapp/
├── data/        # Room database, DAOs, entities, DataStore-backed settings, seed data
├── di/          # Simple manual dependency container
├── domain/      # Program/model logic independent of Android framework
├── repo/        # Repository layer bridging data and UI
└── ui/          # Compose screens, navigation, theme, shared components
```

For the data model, the six correctness invariants the app relies on, the design
system, and a record of bugs found during end-to-end testing, see
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
