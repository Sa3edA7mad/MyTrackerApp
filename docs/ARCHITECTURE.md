# Architecture

## Layers

```
data/    Room database, DAOs, entities, DataStore settings, seed catalog
di/      Manual dependency container (AppContainer) — no Hilt
domain/  Pure program math (domain/Program.kt) and view-facing models (domain/model/)
repo/    TrackerRepository — the single funnel for all data access
ui/      Compose screens, navigation, theme, shared components
```

`domain/Program.kt` has no Android imports and is exercised entirely by JVM unit tests.
Every screen composable takes state and lambdas only — no ViewModel, no `Context`, no
DAO inside a screen composable. `TrackerRepository` is the only place a DAO is referenced
outside `data/`.

## Data model

Four tables, all in `data/entity/Entities.kt`:

- **`exercises`** — the static 29-row catalog (13 program + 8 warm-up + 8 stretch),
  seeded once on database create and never mutated at runtime.
- **`cycles`** — one row per 4-week attempt. Exactly one `isActive = true` at a time.
- **`days`** — per-day state that can't be derived from completions: `warmUpDoneAt`,
  `stretchDoneAt`, `closedAt`. There is deliberately **no** `completedAt` — day
  completion is always computed from the completion count, so it can't drift out of
  sync with the actual data.
- **`completions`** — one row per finished exercise. **Un-ticking deletes the row.**
  Presence of the row *is* the truth, which is what makes every aggregate a plain
  `COUNT(*)`. A unique index on `(cycleId, week, day, circuit, exerciseId)` plus
  `OnConflictStrategy.IGNORE` on insert is what makes a double-tap idempotent.

The `circuit` column on `completions` uses two sentinels alongside the real circuit
numbers: `0` for warm-up, `-1` for stretch (see Invariant 2 below).

## The six invariants

These are the rules the app is built around. Each is named in the source next to the
code that depends on it, and each has direct test coverage.

1. **There is always exactly one active cycle.** The database's `onCreate` callback
   opens the first one; `TrackerRepository.ensureActiveCycle()` is called defensively at
   the head of every read so no screen ever has to handle a null cycle.

2. **Program totals only ever count `circuit >= 1`.** Warm-up and stretch completions
   are stored — so a half-finished routine resumes where you left off — but they must
   never appear in a day, week, or cycle total. Every aggregate query filters on this.

3. **A day is settled when it is fully complete *and* stretched, or the user closed it
   early.** The closed-early escape exists because without it a partly-finished day
   traps the counter forever and the cycle can never reach its completion screen. The
   *and stretched* half of this was added after a bug — see [Bugs found](#bugs-found-and-fixed) below.

4. **Days advance in order; a day past the current position is a read-only preview.**
   Program lets you review a past day or peek at a future one, but a future day's
   checkboxes are disabled until the current day is settled.

5. **Completion is derived, never stored.** A circuit is "done" when it has 13 rows in
   `completions`; a day is "done" when it has as many rows as `exercisesPerDay(week)`.
   Nothing caches this — it's why `days` has no `completedAt` column.

6. **`fallbackToDestructiveMigration()` is forbidden.** This database holds training
   history that can't be regenerated. Any schema change ships a real `Migration`.

## Program shape

`domain/Program.kt` encodes the whole program as data, not formula:

```kotlin
WEEKS = 4
DAYS_PER_WEEK = 6
EXERCISES_PER_CIRCUIT = 13
CIRCUITS_PER_WEEK = [4, 5, 6, 7]   // ramps week to week — a program decision, not math
```

From that: 52/65/78/91 exercises per day across the four weeks, 132 circuits and 1,716
exercises in a full cycle. `nextPosition()` is the single source of truth for "what day
is the user on" — it scans all 24 `(week, day)` positions in order and returns the first
one that isn't settled per invariant 3.

## Design system

Dark-only, flat (no gradients or shadows — depth comes from surface steps and 1dp
outlines). Full token list in `ui/theme/Color.kt`, `Type.kt`, `Spacing.kt`.

- **Canvas** `#0B0D0C`, **Surface** `#16191A`, **Accent** (lime) `#C8FF3D` — reserved
  exclusively for completion and primary actions.
- Category colors (bodyweight/band/warm-up/stretch) are always paired with a text
  label; color alone never carries meaning.
- All type styles use tabular figures (`FontFeatureSettings = "tnum"`) so numeric
  counters like `3/13` don't jitter as digits change width.
- Icons are hand-drawn vector drawables (`res/drawable/ic_*.xml`), not
  `androidx.compose.material.icons` — see the toolchain note in the README.

## Testing

**74 unit tests** (`app/src/test`) — pure JVM, no device needed:

| Suite | Covers |
|---|---|
| `ProgramTest` | `nextPosition`, `isDaySettled`, streaks, the 4am training-day rollover |
| `ProgramTotalsTest` | Derived totals (exercises/circuits per week and per cycle) |
| `HoldTimerTest` | The countdown timer's wall-clock arithmetic |
| `NextUndoneIndexTest` | Guided-pager forward navigation (see bug #2 below) |
| `RecentTalliesTest` | Exercise-detail history bucketing |
| `LibraryFilterTest` | Library search matching |

**53 instrumentation tests** (`app/src/androidTest`) — in-memory Room database, run on
a device/emulator:

| Suite | Covers |
|---|---|
| `SeedTest` | The 29-row catalog seeds correctly on database create |
| `DaoTest` | Raw DAO behavior: idempotent inserts, cascade deletes |
| `RoutineRepositoryTest` | Warm-up/stretch routines against `TrackerRepository` |
| `ProgramStatsRepositoryTest` | Program/Progress screen data derivation |
| `ExerciseDetailRepositoryTest` | Per-exercise history aggregation |
| `CycleRestartTest` | Starting a new cycle after completion |
| `FullCycleTest` | Drives one entire 1,716-completion cycle through the repository |

Run both with `./gradlew testDebugUnitTest connectedDebugAndroidTest`. Both suites
currently pass at 127/127.

## Bugs found and fixed

Found during a full end-to-end pass driving the app on an emulator (not just
unit-level testing). Recorded here because each was a real behavioral defect that
passed a build and would have shipped.

1. **The stretch routine was unreachable.** `isDaySettled` originally checked exercise
   count only. Finishing the last circuit of a day settled it immediately, so the
   counter advanced to the next day before the stretch screen — which always resolves
   to the *current* day — could ever be opened. Fixed by making `isDaySettled` require
   `stretchDone` alongside the exercise count (invariant 3, above).

2. **The guided pager re-walked already-completed exercises.** Advancing to the next
   exercise was a plain `index + 1`; "skip anything already done" was only computed
   once, when the pager first opened. Tick a few exercises in checklist mode, switch to
   guided, and pressing through would walk you back over exercises you'd already
   finished — resetting a per-side exercise back to "side 1" in the process. Fixed by
   extracting `nextUndoneIndex()` and using it on every advance, not just on mount.

3. **"Most done" on the Progress screen had no deterministic tiebreak.** Early in a
   cycle, most exercises are tied at the same completion count, and the SQL `ORDER BY`
   had no secondary sort — so the same data could render in a different order between
   app launches. Fixed with `ORDER BY done DESC, exerciseId ASC`.

4. **The primary button said "Done" on side 1 of a per-side exercise**, when it only
   advances to the side-switch screen. Now reads "Done · side 1".

See `git log` for the commits; the guided-pager fix in particular is covered by
`NextUndoneIndexTest`, which pins the exact scenario that exposed it (ticking a run of
exercises out of order in checklist mode, then resuming guided mode from an earlier
point in the list).
