# MyTrackerApp

An Android app for tracking a workout program, built with Jetpack Compose, Room, and Navigation.
The program's shape (weeks, days, circuit sizes, warm-up/stretch, counting and locking rules) and
its exercise catalog are both editable in-app rather than hard-coded, and the app tracks reps,
load, and body measurements alongside the original checkbox-based completion tracking.

## Features

- **Program rules editor** (Settings → Program rules) — weeks, days per week, circuits per week,
  warm-up/stretch toggles, whether they count toward totals, whether future days are locked, the
  day-rollover hour, and display units. Edits save to a *draft* and only affect a running cycle
  once you explicitly apply them; the impact (days reopened, completions orphaned) is shown before
  you confirm.
- **Exercise catalog editor** (Library → `+` / edit) — add, edit, reorder, archive and restore
  exercises; archiving keeps an exercise's history readable without it staying in the rotation.
  Program-slot exercises drive the *draft* rules' circuit size live; a running cycle keeps the
  composition it was snapshotted with until rules are applied.
- **Rep/load logging** — an exercise can be flagged to prompt for reps and/or load when ticked.
  The prompt pre-fills from the exercise's last logged set (or its weekly target/default), and
  skipping the prompt never blocks finishing a circuit. Exercises without logging enabled behave
  exactly as a plain checkbox, unchanged.
- **Body measurements** (Progress / Settings → Body measurements) — log a whole measuring session
  at once against a catalog of 17 default metrics (or your own custom ones), see per-metric
  history with a trend sparkline, and derived stats (BMI, waist-to-hip, waist-to-height, lean
  mass). Values are always stored in kilograms/centimetres/percent; the unit toggle only affects
  display.

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

## Program rules

Every rule of the program — weeks, days per week, circuit sizes, warm-up/stretch, counting and
locking — lives in `domain/ProgramRules.kt` as one immutable value, not as scattered constants.
Two invariants make editing safe:

- **Rules are snapshotted per cycle** (`cycle_rules` table). Editing the rules writes a new
  *draft* (`program_rules` table); a cycle already in progress keeps reading the rules it was
  started under until you explicitly apply the draft to it. This is what keeps a finished cycle's
  percentage meaning the same thing tomorrow as it did the day it was earned.
- **Rule edits never delete completion history.** A completion that falls outside the new rules
  (e.g. a circuit shrinks below where it was ticked) is *orphaned* — kept in the database, excluded
  from totals — never deleted. `RuleImpact.analyse` reports how many rows this affects before you
  confirm the change.

See the numbered `INVARIANT n` comments throughout `data/db/`, `data/entity/Entities.kt` and
`domain/` for the full set (nine in total) and where each one is enforced.

## Known toolchain quirks

- **No Compose icon library is available** on this BOM — icons are drawn as local vector drawables in `app/src/main/res/drawable/ic_*.xml` instead of `androidx.compose.material.icons`.
- **KSP requires `android.disallowKotlinSourceSets=false`** in `gradle.properties` (already set) because AGP 9's built-in Kotlin otherwise rejects the source sets KSP registers for Room's generated code.

## Project structure

```
app/src/main/java/com/example/mytrackerapp/
├── data/
│   ├── db/        # Room database, DAOs, versioned migrations (currently v1-v5)
│   ├── entity/     # Table entities, incl. program_rules/cycle_rules and metrics/measurements
│   ├── prefs/      # DataStore-backed settings
│   └── seed/       # Default exercise catalog + metric catalog seed rows
├── di/            # Simple manual dependency container (AppContainer)
├── domain/        # Program/model logic independent of Android framework
│   ├── ProgramRules.kt        # the program's rules as one immutable value
│   ├── RuleValidation.kt      # rule validation + apply-impact analysis
│   ├── CatalogValidation.kt   # exercise-draft validation
│   ├── PerformanceStats.kt    # best load/reps, volume, trend from set logs
│   ├── BodyStats.kt           # BMI, waist-to-hip/height, lean mass, metric trends
│   ├── Units.kt                # kg/lb, cm/in display conversion
│   └── model/                  # Exercise/CircuitView/DayState/... UI-facing models
├── repo/          # Repository layer bridging data and UI
│   ├── TrackerRepository.kt     # program/circuit/completion reads and writes
│   ├── RulesRepository.kt       # draft + per-cycle rule snapshots, apply/preview
│   ├── CatalogRepository.kt     # exercise CRUD, archive/restore, reorder
│   └── MeasurementRepository.kt # metric catalog + measurement CRUD
└── ui/
    ├── screens/
    │   ├── today/, program/, progress/, library/   # the four tabbed screens
    │   ├── circuit/, routine/                        # guided/checklist training flow
    │   ├── rules/                                     # program rules editor
    │   ├── catalog/                                   # exercise add/edit/archive
    │   ├── exercise/                                   # exercise detail + performance
    │   ├── measure/                                    # measurements list, history, metric catalog
    │   ├── settings/, complete/
    ├── components/    # Shared themed composables (buttons, rows, steppers, dialogs)
    └── theme/         # Colours, spacing, typography
```
