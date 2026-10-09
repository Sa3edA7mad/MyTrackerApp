# MyTrackerApp

A native Android tracker for workout programs, built with Jetpack Compose, Room, and
Navigation Compose. Offline-only — no accounts, no network calls. You can run several
programs side by side (a home bodyweight program, a gym lifting split, CrossFit-style
conditioning…), each with its own cycle, named circuits, sets, warm-up, stretch and rules.
The programs, their shape and the exercise library are all editable in-app rather than
hard-coded, and tracking goes beyond a checkbox: sets, reps, load, timed formats and body
measurements are all first-class.

## Features

- **Today** — the current day's circuits, warm-up, and stretch, with live progress. With
  more than one active program, a chip row picks which program's day you're looking at.
- **Programs** — Home 4-Week (your original program), plus Gym Strength and CrossFit
  Conditioning starters and any you create. Any number can be active; each runs its own
  cycle with its own week/day counter. Pause, archive and restore keep history intact.
- **Program editor** — name, active toggle, rules, named circuits (exercises with sets,
  target and load overrides; straight-sets or rounds order; Standard / EMOM / Interval /
  AMRAP / For-time format), which circuits each day of the week runs, and per-program
  warm-up and stretch lists picked from the library. Edits apply to the next cycle unless
  you apply them to the current one.
- **Guided circuit mode** — one exercise at a time, with an auto-advancing hold timer
  (audio cues at 3-2-1-0, since the phone is usually out of easy reach mid-hold) for
  timed exercises, and a two-stage side-1/side-2 flow for per-side exercises. An exercise
  flagged to track reps and/or load prompts for them when ticked, pre-filled from its last
  logged set (or its weekly target/default); skipping the prompt never blocks finishing a
  circuit, and exercises without logging enabled stay a plain single-tap checkbox. Multi-set
  exercises run one set per step ("Set 2 of 5") with an optional rest countdown between
  sets; EMOM, interval, AMRAP and for-time circuits get a clock card (AMRAP counts rounds,
  for-time records your finish time, and the score is saved per circuit slot). A library
  technique cue shows as a highlighted tip.
- **Checklist mode** — the same circuit as a flat, tickable list. Switching modes
  mid-circuit preserves progress and resumes at the right exercise either way.
- **Program grid** (Programs tab) — the selected program's weeks at a glance; past days are
  reviewable, future days are a locked preview until the current day is finished, unless
  that lock is turned off in that program's rules.
- **Program rules editor** (a program's editor → Rules; Settings → Program rules opens
  Home's) — weeks, days per week, rounds per day, warm-up/stretch toggles, whether they count toward totals, whether future days are
  locked, the day-rollover hour, and display units. Edits save to a *draft* and only affect
  a running cycle once explicitly applied, with a preview of the impact (days reopened,
  completions orphaned) shown first.
- **Library & exercise catalog editor** — 152 exercises (Home's 29 plus the Gym, CrossFit,
  Mobility & Flexibility and Core sheets), searchable by name, muscle group or equipment and
  filterable by level, each with equipment, level, technique cue, target and a form-video
  link with its title and channel. Add, edit, reorder, archive
  and restore exercises; archiving keeps an exercise's history readable without it staying
  in the rotation. Program-slot exercises drive the *draft* rules' circuit size live; a
  running cycle keeps the composition it was snapshotted with until rules are applied.
- **Progress** — streak, cycle completion %, a heat map of the whole cycle, and a
  "most done" ranking for the selected program. The streak counts a day trained in any
  program.
- **Body measurements** (Progress / Settings → Body measurements) — log a whole measuring
  session at once against a catalog of 17 default metrics (or your own custom ones), see
  per-metric history with a trend sparkline, and derived stats (BMI, waist-to-hip,
  waist-to-height, lean mass). Values are always stored in kilograms/centimetres/percent;
  the unit toggle only affects display.
- **Cycle completion** — a summary screen once every day is settled, with the option to
  start a fresh cycle without losing history.
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

Run unit tests (pure domain logic, no Android dependency):

```bash
./gradlew testDebugUnitTest
```

Run instrumented tests (Room migrations, repository behaviour, and the end-to-end UI suite in
`app/src/androidTest/.../e2e/`, which drives the real app screen by screen; needs a connected
device/emulator, and uninstalls the app afterwards, wiping its data):

```bash
./gradlew connectedDebugAndroidTest
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#testing) for what each suite actually
guards and why. Every feature, its exact on-screen behaviour, and a regression checklist keyed to
those tests are in [`docs/FEATURES.md`](docs/FEATURES.md).

## Tech stack

- Kotlin 2.2.10, AGP 9.2.1 (AGP's built-in Kotlin — no separate `org.jetbrains.kotlin.android` plugin)
- Jetpack Compose (BOM 2026.02.01) + Material 3
- Room 2.8.5 (via KSP) for persistence
- Navigation Compose for screen routing
- DataStore Preferences for settings

## Program rules

Every rule of a program — weeks, days per week, circuit sizes, warm-up/stretch, counting and
locking — lives in `domain/ProgramRules.kt` as one immutable value, not as scattered constants.
What a program *contains* (its named circuits, day rotation, warm-up and stretch) is its plan
in `domain/Plan.kt`; see "Programs" in `docs/ARCHITECTURE.md`.
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
- **Avoid `rememberUpdatedState(::localFunction)`** — a callable reference to a local
  function compares equal across recompositions, so the backing state silently never
  updates. See `docs/ARCHITECTURE.md` for the bug this caused and the fix.

## Project structure

```
app/src/main/java/com/example/mytrackerapp/
├── data/
│   ├── db/        # Room database, DAOs, versioned migrations (currently v1-v7)
│   ├── entity/     # Table entities, incl. program_rules/cycle_rules and metrics/measurements
│   ├── prefs/      # DataStore-backed settings
│   └── seed/       # Home catalog, 123-row library import, starter programs, metric catalog
├── di/            # Simple manual dependency container (AppContainer)
├── domain/        # Program/model logic independent of Android framework
│   ├── ProgramRules.kt        # a program's rules as one immutable value (per-day rotations)
│   ├── Plan.kt                # circuits, sets, set order, timed formats; plan text codec
│   ├── RuleValidation.kt      # rule validation + apply-impact analysis
│   ├── CatalogValidation.kt   # exercise-draft validation
│   ├── PerformanceStats.kt    # best load/reps, volume, trend from set logs
│   ├── BodyStats.kt           # BMI, waist-to-hip/height, lean mass, metric trends
│   ├── Units.kt                # kg/lb, cm/in display conversion
│   └── model/                  # Exercise/CircuitView/DayState/... UI-facing models
├── repo/          # Repository layer bridging data and UI
│   ├── TrackerRepository.kt     # program/circuit/completion reads and writes
│   ├── RulesRepository.kt       # one program's draft rules + plan, per-cycle snapshots, apply/preview
│   ├── ProgramRepository.kt     # create/rename/activate/archive programs
│   ├── CatalogRepository.kt     # exercise CRUD, archive/restore, reorder
│   └── MeasurementRepository.kt # metric catalog + measurement CRUD
└── ui/
    ├── screens/
    │   ├── today/, program/, progress/, library/   # the four tabbed screens
    │   ├── programedit/                                # program + circuit editors
    │   ├── circuit/, routine/                        # guided/checklist flow, rest and workout clock
    │   ├── rules/                                     # program rules editor
    │   ├── catalog/                                   # exercise add/edit/archive
    │   ├── exercise/                                   # exercise detail + performance
    │   ├── measure/                                    # measurements list, history, metric catalog
    │   ├── settings/, complete/
    ├── components/    # Shared themed composables (buttons, rows, steppers, dialogs)
    └── theme/         # Colours, spacing, typography
```

For the data model, the nine correctness invariants the app relies on, the design
system, and a record of bugs found during end-to-end testing, see
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
