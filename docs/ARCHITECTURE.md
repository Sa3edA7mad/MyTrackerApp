# Architecture

## Layers

```
data/    Room database, DAOs, entities, DataStore settings, seed catalog, migrations (v1-v5)
di/      Manual dependency container (AppContainer) — no Hilt
domain/  Pure program/rule math (ProgramRules.kt, RuleValidation.kt, CatalogValidation.kt,
         PerformanceStats.kt, BodyStats.kt, Units.kt) and view-facing models (domain/model/)
repo/    TrackerRepository, RulesRepository, CatalogRepository, MeasurementRepository —
         the only places a DAO is referenced outside data/
ui/      Compose screens, navigation, theme, shared components
```

Everything in `domain/` has no Android imports and is exercised entirely by JVM unit tests.
Every screen composable takes state and lambdas only — no ViewModel, no `Context`, no DAO
inside a screen composable.

## Data model

Eight tables, all in `data/entity/Entities.kt`:

- **`exercises`** — the catalog, seeded with 29 rows on database create but fully mutable
  at runtime: `slot` (PROGRAM/WARMUP/STRETCH) decides circuit membership, `enabled` and
  `archivedAt` control whether it's in the rotation, and `tracksReps`/`tracksLoad`/
  `defaultLoadKg`/`defaultBandLevel`/`progressionStep` drive rep/load logging.
- **`program_rules`** — the single editable *draft* row for the program's shape (weeks,
  days, circuits per week, warm-up/stretch, counting/locking toggles, display units).
- **`cycle_rules`** — the rules a cycle was started under, frozen at creation time
  (Invariant 7), including the exact ordered exercise ids that made up its circuit.
- **`cycles`** — one row per attempt at the program. Exactly one `isActive = true` at a time.
- **`days`** — per-day state that can't be derived from completions: `warmUpDoneAt`,
  `stretchDoneAt`, `closedAt`. There is deliberately **no** `completedAt` — day completion
  is always computed from the completion count (and, since the stretch-gate fix, from
  `stretchDoneAt`), so it can't drift out of sync with the actual data.
- **`completions`** — one row per finished exercise, plus nullable `reps`/`loadKg`/
  `bandLevel`/`holdSeconds`/`rpe`/`note` detail columns. **Un-ticking deletes the row.**
  Presence of the row *is* the truth, which is what makes every aggregate a `COUNT(*)` —
  the detail columns are never counted on. A unique index on
  `(cycleId, week, day, circuit, exerciseId)` plus `OnConflictStrategy.IGNORE` on insert is
  what makes a double-tap idempotent.
- **`metrics`** — the body-measurement catalog: 17 default rows plus any custom ones,
  each with a kind (WEIGHT/LENGTH/PERCENT/COUNT), enabled flag, and soft-delete `archivedAt`.
- **`measurements`** — one row per logged reading, always stored in canonical units
  (kilograms, centimetres, percent) regardless of the display unit setting.

The `circuit` column on `completions` uses two sentinels alongside the real circuit
numbers: `0` for warm-up, `-1` for stretch (see Invariant 2 below).

## The nine invariants

These are the rules the app is built around. Each is named in the source next to the code
that depends on it, and each has direct test coverage.

1. **There is always exactly one active cycle.** The database's `onCreate` callback opens
   the first one; `TrackerRepository.ensureActiveCycle()` is called defensively at the head
   of every read so no screen ever has to handle a null cycle.

2. **Program totals only ever count `circuit >= 1`, unless `countRoutinesInTotals` is on.**
   Warm-up and stretch completions are always stored — so a half-finished routine resumes
   where you left off — but by default they never appear in a day, week, or cycle total.
   Every aggregate query takes an `:includeRoutines` parameter and filters on this.

3. **A day is settled when it is fully complete *and* stretched (when stretch is enabled),
   or the user closed it early.** The closed-early escape exists because without it a
   partly-finished day traps the counter forever and the cycle can never reach its
   completion screen. The *and stretched* half was added after a bug — see
   [Bugs found](#bugs-found-and-fixed) below.

4. **Days advance in order; a day past the current position is a read-only preview, unless
   `lockFutureDays` is off.** Program lets you review a past day or peek at a future one,
   but a future day's checkboxes are disabled until the current day is settled — unless
   that lock has been turned off in Program rules.

5. **Completion is derived, never stored.** A circuit is "done" when it has as many rows in
   `completions` as the rules say it should; a day is "done" when it has as many rows as
   `exercisesPerDay(week)`. Nothing caches this. The rep/load/RPE/note detail columns on
   `completions` are all nullable and never backfilled, so every aggregate stays a plain
   `COUNT(*)` and can never depend on a detail column being present.

6. **`fallbackToDestructiveMigration()` is forbidden.** This database holds training
   history that can't be regenerated. Any schema change ships a real `Migration` — five so
   far (v1 through v5), each with a `MigrationTestHelper` test.

7. **A cycle's rules are frozen in `cycle_rules` at cycle creation.** Reads for a cycle use
   that snapshot, never the editable `program_rules` draft. This is what keeps a finished
   cycle's percentage meaning the same thing tomorrow as it did the day it was earned, even
   if the program rules or the exercise catalog change afterwards.

8. **Rule and catalog edits never delete completion history.** A completion that no longer
   fits the current rules (e.g. a circuit shrinks below where it was ticked, or its
   exercise is archived) is *orphaned* — kept in the table, excluded from totals — never
   deleted. `RuleImpact.analyse` reports how many rows this affects before a change is
   applied.

9. **The fresh-install path and the migration path must agree.** `SeedCallback.onCreate`
   and every `Migration` seed the same `program_rules`/`metrics` content, so a brand-new
   install and one migrated from v1 end up identical.

## Program rules

`domain/ProgramRules.kt` encodes the whole program's shape as one immutable value, not as
formula-backed constants:

```kotlin
data class ProgramRules(
    val weeks: Int = 4,
    val daysPerWeek: Int = 6,
    val circuitsPerWeek: List<Int> = listOf(4, 5, 6, 7),   // ramps week to week
    val exercisesPerCircuit: Int = 13,                      // derived from the catalog
    val warmUpCount: Int = 8,
    val stretchCount: Int = 8,
    val dayRolloverHour: Int = 4,
    val warmUpEnabled: Boolean = true,
    val stretchEnabled: Boolean = true,
    val countRoutinesInTotals: Boolean = false,
    val lockFutureDays: Boolean = true
)
```

The shipped defaults reproduce the original fixed program: 52/65/78/91 exercises per day
across the four weeks, 132 circuits and 1,716 exercises in a full cycle.
`ProgramRules.nextPosition()` is the single source of truth for "what day is the user on"
— it scans every `(week, day)` position in program order and returns the first one that
isn't settled per Invariant 3. Editing rules writes a new draft (`program_rules`); a
running cycle keeps reading the snapshot it started with (`cycle_rules`, Invariant 7) until
the draft is explicitly applied to it via `RulesRepository.applyDraftToCycle`.

## Design system

Three palettes (`CharcoalPalette`, `SteelPalette`, `FrostPalette` in `ui/theme/Color.kt`) are
provided through `LocalTrackerPalette`; the legacy color names (`Canvas`, `Accent`, …) are composable
getters that read the active palette. The user picks one in Settings → Appearance (`ThemeMode` in
`SettingsStore`); **Match device** maps dark mode to charcoal and light mode to frost. Depth comes
from surface steps, hairlines and `Modifier.glassBackdrop()` (two radial glows, no real blur).
Contrast is enforced by `PaletteContrastTest`. Colors must be read in a composable, never inside a
`Canvas {}`/`drawBehind {}` lambda: hoist them into a local `val` first.

- Category colors (bodyweight/band/warm-up/stretch) are always paired with a text
  label; color alone never carries meaning.
- All type styles use tabular figures (`FontFeatureSettings = "tnum"`) so numeric
  counters like `3/13` don't jitter as digits change width.
- Icons are hand-drawn vector drawables (`res/drawable/ic_*.xml`), not
  `androidx.compose.material.icons` — see the toolchain note in the README.

## Testing

Unit tests (`app/src/test`) — pure JVM, no device needed. Notable suites:

| Suite | Covers |
|---|---|
| `ProgramRulesTest` | `nextPosition`, `isDaySettled` (incl. the stretch gate), derived totals |
| `RuleValidationTest` / `RuleImpactTest` | Rule-edit validation and apply-impact analysis |
| `CatalogValidationTest` | Exercise-draft validation |
| `PerformanceStatsTest` / `BodyStatsTest` / `UnitsTest` | Set-log stats, BMI/WHR/lean mass, unit conversion |
| `TargetForWeekTest` | Per-week progression on an exercise's target |
| `HoldTimerTest` | The countdown timer's wall-clock arithmetic |
| `NextUndoneIndexTest` | Guided-pager forward navigation (see bug #2 below) |
| `RecentTalliesTest` / `LibraryFilterTest` | Exercise-detail history bucketing, library search |

Instrumented tests (`app/src/androidTest`) — in-memory Room database, run on a
device/emulator. Notable suites:

| Suite | Covers |
|---|---|
| `MigrationTest` | Every schema migration v1→v5, plus fresh-install/migrated-install parity (Invariant 9) |
| `SeedTest` / `DaoTest` | Catalog seeding, idempotent inserts, cascade deletes |
| `RulesSnapshotTest` / `RulesApplyTest` / `OrphanFilterTest` / `InvariantTogglesTest` | The rules read/write/apply/orphan-filtering path |
| `CatalogRulesTest` / `CatalogCrudTest` | Exercise CRUD and its effect on the draft vs. a running cycle |
| `SetDetailTest` / `MeasurementRepositoryTest` | Rep/load logging and body-measurement CRUD |
| `RoutineRepositoryTest` / `ProgramStatsRepositoryTest` / `ExerciseDetailRepositoryTest` | Warm-up/stretch, Program/Progress, and exercise-history derivation |
| `CycleRestartTest` | Starting a new cycle after completion |
| `FullCycleTest` | Drives one entire 1,716-completion cycle through the repository |

Run both with `./gradlew testDebugUnitTest connectedDebugAndroidTest`.

## Bugs found and fixed

Found during full end-to-end passes driving the app on an emulator (not just unit-level
testing). Recorded here because each was a real behavioral defect that passed a build and
would have shipped.

1. **The stretch routine was unreachable.** `isDaySettled` originally checked exercise
   count only. Finishing the last circuit of a day settled it immediately, so the counter
   advanced to the next day before the stretch screen — which always resolves to the
   *current* day — could ever be opened. Fixed by making `ProgramRules.isDaySettled`
   require `stretchDone` alongside the exercise count whenever `stretchEnabled` is on
   (Invariant 3, above).

2. **The guided pager re-walked already-completed exercises.** Advancing to the next
   exercise was a plain `index + 1`; "skip anything already done" was only computed once,
   when the pager first opened. Tick a few exercises in checklist mode, switch to guided,
   and pressing through would walk you back over exercises you'd already finished —
   resetting a per-side exercise back to "side 1" in the process. Fixed by extracting
   `nextUndoneIndex()` and using it on every advance, not just on mount.

3. **"Most done" on the Progress screen had no deterministic tiebreak.** Early in a cycle,
   most exercises are tied at the same completion count, and the SQL `ORDER BY` had no
   secondary sort — so the same data could render in a different order between app
   launches. Fixed with `ORDER BY done DESC, exerciseId ASC`.

4. **The primary button said "Done" on side 1 of a per-side exercise**, when it only
   advances to the side-switch screen. Now reads "Done · side 1".

5. **The log sheet for rep/load entry wasn't scrollable.** With several metrics or
   tracking fields enabled, the Save button could sit below the visible area of a
   `ModalBottomSheet` with no way to scroll to it. Fixed by wrapping the sheet's content in
   a `verticalScroll` capped to 90% of the screen height.

See `git log` for the commits; the guided-pager fix in particular is covered by
`NextUndoneIndexTest`, which pins the exact scenario that exposed it (ticking a run of
exercises out of order in checklist mode, then resuming guided mode from an earlier point
in the list).
