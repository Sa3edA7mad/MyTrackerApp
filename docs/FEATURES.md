# MyTrackerApp: feature reference and regression checklist

This is the single source of truth for **what the app does** and **how to prove it still does it**.
It is written to be executed literally, by a person or by an LLM agent driving the Android
emulator. Every test case has a stable ID, exact on-screen text, and the expected result. Where an
automated test exists, it is named next to the case.

- **Audience:** human testers, LLM test agents, and developers checking a change for regressions.
- **App version covered:** database schema v6, commit range up to this document's commit.
- **Scope:** every user-visible feature, plus the data rules behind the numbers on screen.

---

## Contents

0. [How to use this document](#0-how-to-use-this-document)
1. [Glossary and reference numbers](#1-glossary-and-reference-numbers)
2. [Screen map](#2-screen-map)
3. [Seed data](#3-seed-data)
4. [Business rules and invariants](#4-business-rules-and-invariants)
5. [Precondition recipes](#5-precondition-recipes)
6. [Test cases by feature](#6-test-cases-by-feature)
   NAV · TOD · RTN · GUI · TMR · LST · LOG · PRG · PRS · LIB · CAT · RUL · SET · DAT · MEA · CYC · INV
7. [Smoke suite](#7-smoke-suite)
8. [Manual-only checks](#8-manual-only-checks)
9. [Automated test inventory](#9-automated-test-inventory)
10. [Known issues and limitations](#10-known-issues-and-limitations)
11. [Regression log: bugs fixed in this pass](#11-regression-log-bugs-fixed-in-this-pass)

---

## 0. How to use this document

### 0.1 Running the automated suites

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew connectedDebugAndroidTest
```

- `testDebugUnitTest`: about 128 JVM tests of the pure domain logic (rules, stats, units). It runs in seconds and needs no device.
- `connectedDebugAndroidTest`: about 190 on-device tests, about 9 minutes on a Pixel emulator. It covers DAO, migration and
  repository tests, **plus the end-to-end UI suite** in `app/src/androidTest/.../e2e/`. That suite drives the real `MainActivity`
  against the real database and DataStore.
- Run only the UI suite: add `-Pandroid.testInstrumentationRunnerArguments.package=com.example.mytrackerapp.e2e`.
- Run one class: `-Pandroid.testInstrumentationRunnerArguments.class=com.example.mytrackerapp.e2e.TodayE2eTest`.
- `connectedAndroidTest` **uninstalls the app afterwards, wiping its data**. Don't run it on a device with real training history.
- Emulator tip: run `adb shell svc power stayon true` first. Taps silently stop landing once the display dims.

### 0.2 Running the manual checklist

1. Reset to a fresh install (recipe **R0**) before each area, unless a case says otherwise.
2. Run the cases top to bottom. Each case's **Pre** column names the recipe(s) to apply first.
3. Record each case as `PASS`, `FAIL` (with a screenshot and the actual text), or `BLOCKED`.

### 0.3 Notation (read this before running any case)

| Notation | Meaning |
|---|---|
| `Text` in backticks | Exact on-screen text. Match it character for character, including `·` (middle dot), `—` (em dash), `→`, `⇄`, `✓` and double spaces. |
| **Buttons are upper-case** | Primary and ghost buttons render their label in capitals: code `Start circuit 1` shows as `START CIRCUIT 1`. This document always writes buttons as they **appear**. |
| Section headers are upper-case | For example `CIRCUITS`, `HOW TO`, `YOUR HISTORY`. Stat-tile captions too (`STREAK`, `BMI`). |
| `[desc: X]` | An icon with no visible text. Find it by its accessibility label (content description) `X`. For example `[desc: Settings]` is the gear icon. |
| "tap card `Circuit 2`" | Tap the row or card that contains that text. |
| **P0 / P1 / P2** | P0 = smoke (critical path, run on every build). P1 = core behaviour. P2 = edge cases and regressions. |
| **Auto** | The automated test covering the case: `ClassName.testName`. `—` means manual only. |

### 0.4 Result template

```
Build: <commit sha>   Device: <model / API>   Tester: <name or agent>   Date: <yyyy-mm-dd>
ID      Result   Notes
TOD-01  PASS
TOD-02  FAIL     CTA read "START CIRCUIT 1", expected "WARM UP, THEN CIRCUIT 1" (screenshot 3)
```

---

## 1. Glossary and reference numbers

| Term | Meaning |
|---|---|
| **Cycle** | One run through the whole program (default 4 weeks). Exactly one cycle is active at a time. Older cycles are kept. |
| **Position / day** | A (week, day) pair, for example W1 D1. Days are in program order: W1 D1…D6, then W2 D1… |
| **Circuit** | One pass through the program exercises. A day has N circuits; N depends on the week. |
| **Program exercise** | An exercise in the `PROGRAM` slot. Every circuit contains all of them (default 13). |
| **Routine** | Warm-up (8 moves) or stretch (8 stretches). Done once per day, not once per circuit. |
| **Completion** | One ticked exercise in one circuit on one day. All totals are counts of completions. |
| **Settled day** | A day where every circuit exercise is done **and** its stretch is done or skipped (when stretch is enabled), **or** the day was ended early. Settling moves the counter to the next day. |
| **Current day** | The first unsettled day. Days after it are **future days**: locked by default, readable as a preview. |
| **End day early / closed** | Settles a partly done day on purpose. The finished work is kept. |
| **Draft rules** | The editable rule set (Settings → Program rules). Saving only affects the **next** cycle. |
| **Snapshot** | The frozen copy of the rules and program composition a cycle runs on. It changes only when you tap `APPLY TO CURRENT CYCLE`. |
| **Orphaned completion** | A completion that no longer fits the rules (for example, its circuit was removed). It is kept but excluded from every total. |
| **Archive** | Soft delete. The item leaves the rotation and lists, but its history stays readable. |
| **Guided mode / list mode** | Two ways to run a circuit: one exercise at a time (guided, the default), or a flat checklist (list). |

**Reference numbers for the shipped (default) program.** Use these as the expected values.

| Quantity | Value |
|---|---|
| Weeks × days | 4 × 6 = **24 days** |
| Circuits per day, weeks 1–4 | **4, 5, 6, 7** |
| Circuits per week, weeks 1–4 | 24, 30, 36, 42 (**132** per cycle) |
| Exercises per circuit | **13** (6 bodyweight + 7 band) |
| Exercises per day, weeks 1–4 | **52, 65, 78, 91** |
| Exercises per week, weeks 1–4 | 312, 390, 468, 546 |
| Exercises per cycle | **1716** |
| Warm-up moves / stretches | 8 / 8. They are **not** counted in totals by default. |
| Catalog size | **29** (13 program + 8 warm-up + 8 stretch) |
| Default metrics | 17, of which **9** are enabled: Body weight, Body fat, Height, Chest, Upper arm (R), Waist, Hips, Thigh (R), Resting heart rate |
| Day rollover hour | 04:00. A set done at 01:00 counts for the previous calendar day. |
| Percent | Integer division (floor). For example 52/1716 = 3 %, and 13/1716 = 0 %. |

---

## 2. Screen map

```
Bottom bar (only on the 4 tabs): TODAY · PROGRAM · PROGRESS · LIBRARY

TODAY ──[desc: Settings]──────────▶ Settings ──Program rules────▶ Program rules
  │    ──[desc: End day early]──▶ dialog        ──Body measurements─▶ Body measurements
  │    ──Warm-up card / CTA──────▶ Warm-up (guided | list)            ├─[desc: Log measurements]▶ log sheet
  │    ──Circuit N card / CTA────▶ Circuit (guided | list)            ├─metric card──▶ Metric history
  │    ──Stretch card / CTA──────▶ Stretch (guided | list)            └─Edit metrics──▶ Edit metrics
  └─(cycle finished) SEE CYCLE SUMMARY ──▶ Cycle summary
PROGRAM ──day square──▶ expands ──Circuit N──▶ Circuit
PROGRESS ──Body measurements──▶ Body measurements
LIBRARY ──[desc: Add exercise]──▶ New exercise
        ──exercise row──▶ Exercise detail ──[desc: Edit exercise]──▶ Edit exercise
```

- The bottom bar is hidden on every screen that isn't a tab. `[desc: Back]` (top-left arrow) or system Back returns.
- Circuit and routine screens close with `[desc: Close circuit]` (the X, top left) or system Back.
- Switching tabs keeps each tab's state (for example the Library filter). Re-tapping the current tab does nothing.

---

## 3. Seed data

A fresh install has exactly this catalog. Circuits run the program exercises in this order.

| # | id | Name | Category (section header) | Muscles | Target | Per side | Target label |
|---|---|---|---|---|---|---|---|
| 1 | squat | Squat | BODYWEIGHT | Legs · Glutes · Core | 5 reps | – | 5 reps |
| 2 | push_up | Push-up | BODYWEIGHT | Chest · Shoulders · Triceps | 5 reps | – | 5 reps |
| 3 | dead_hang | Dead Hang | BODYWEIGHT | Shoulders · Spine · Grip | **15 sec (timed)** | – | 15 sec |
| 4 | crunch | Crunch | BODYWEIGHT | Core · Abs | 8 reps | – | 8 reps |
| 5 | superman | Superman | BODYWEIGHT | Lower Back · Glutes | 5 reps | – | 5 reps |
| 6 | glute_bridge | Glute Bridge | BODYWEIGHT | Glutes · Hamstrings · Core | 8 reps | – | 8 reps |
| 7 | bicep_curl | Bicep Curl | RESISTANCE BAND | Biceps | 8 reps | – | 8 reps |
| 8 | tricep_pushdown | Tricep Pushdown | RESISTANCE BAND | Triceps | 8 reps | – | 8 reps |
| 9 | pull_apart | Pull-Apart | RESISTANCE BAND | Rear Delts · Upper Back | 10 reps | – | 10 reps |
| 10 | external_rotation | External Rotation | RESISTANCE BAND | Rotator Cuff | 10 reps | **yes** | 10 reps each side |
| 11 | internal_rotation | Internal Rotation | RESISTANCE BAND | Rotator Cuff | 10 reps | **yes** | 10 reps each side |
| 12 | shoulder_press | Shoulder Press | RESISTANCE BAND | Shoulders · Triceps | 8 reps | – | 8 reps |
| 13 | band_row | Band Row | RESISTANCE BAND | Back · Biceps · Rear Delts | 10 reps | – | 10 reps |

- **Warm-up** (section `WARM-UP`, 8 moves, all rep-based, no muscles text): Neck Rolls (5 each way), Shoulder Rolls
  (10 total), Arm Circles (10 each way), Hip Circles (10 each way), Leg Swings (10 each leg), Ankle Rolls (10 each foot),
  Cat-Cow (8 cycles), Easy Air Squat (8 reps).
- **Stretch** (section `STRETCH`, 8, all timed): Quad Stretch, Hamstring Stretch, Hip Flexor Stretch, Lat Stretch,
  Shoulder Cross (Band), Spinal Twist (each 30 sec, per side); Chest Opener (Band) (30 sec); Child's Pose (45 sec).
- **Form videos:** Push-up, Glute Bridge, Pull-Apart and Band Row link to specific YouTube videos. Every other exercise,
  including new ones with a blank URL, opens a YouTube search for **the exercise name only**, with no suffix.

**Default metrics** (kind → canonical unit, then display decimals):

| id | Name | Kind | Enabled | Decimals |
|---|---|---|---|---|
| bodyweight | Body weight | WEIGHT (kg) | yes | 1 |
| body_fat | Body fat | PERCENT (%) | yes | 1 |
| height | Height | LENGTH (cm) | yes | 0 |
| neck, shoulders | Neck, Shoulders | LENGTH | no | 1 |
| chest | Chest | LENGTH | yes | 1 |
| upper_arm_left / _right | Upper arm (L) / (R) | LENGTH | L no / R yes | 1 |
| forearm_left / _right | Forearm (L) / (R) | LENGTH | no | 1 |
| waist, hips | Waist, Hips | LENGTH | yes | 1 |
| thigh_left / _right | Thigh (L) / (R) | LENGTH | L no / R yes | 1 |
| calf_left / _right | Calf (L) / (R) | LENGTH | no | 1 |
| resting_hr | Resting heart rate | COUNT (no unit) | yes | 0 |

---

## 4. Business rules and invariants

These are the rules behind every number. A regression in any of them is a **P0 data bug**, even if the screen looks fine.

| # | Rule | Where it shows |
|---|---|---|
| R-1 | Exactly one active cycle always exists. A fresh install opens on W1 D1. | Today |
| R-2 | By default, warm-up and stretch completions are stored but **never counted** in any day, week or cycle total. The rules toggle *Count warm-up and stretch in totals* changes this. | Today, Program, Progress |
| R-3 | A day settles when every circuit exercise is done **and** its stretch is done or skipped (stretch only counts while it's enabled in the rules), or when it is ended early. Nothing else moves the counter. Ticking a warm-up alone never settles a day. | Today, Program |
| R-4 | Days after the current day are **locked**: guided mode shows a read-only preview. List mode can still tick them, and a note says which day the ticks count for. The rules toggle *Lock future days* removes the lock. | Circuit, Program |
| R-5 | Every total is derived by counting completion rows. Nothing is cached. Ticking the same exercise twice never double-counts. | everywhere |
| R-6 | Database upgrades never wipe data (no destructive migrations). | upgrade |
| R-7 | A running cycle uses its **snapshot**: rules, exercises per circuit, and the exact program exercises in order. Editing rules or the catalog only changes the **draft**, until you tap `APPLY TO CURRENT CYCLE`. A new cycle is snapshotted from the draft. | Circuit, Rules |
| R-8 | Rule edits never delete completions. Rows that no longer fit are orphaned: kept, and excluded from totals. Exercises and metrics are archived, not deleted. **The only hard delete in the app is a measurement reading.** | Rules, Library, Measurements |
| R-9 | A fresh install and an upgraded install end up with identical rules, snapshots and metrics. | install / upgrade |
| R-10 | Because of R-3, a day whose circuits are finished stays the current day (Today, Program, warm-up and stretch) until its stretch is done or skipped. Ticking the next day from list view doesn't release it. Ending the day early does. | Today, Program |
| R-11 | Measurements are stored in kg / cm / %. The unit toggle changes display and input only; stored values never convert. | Measurements, logging |
| R-12 | At least one program exercise must stay enabled. Archive, disable, and moving it to another slot are all refused with `At least one program exercise must stay enabled.` | Exercise editor |
| R-13 | Exercise ids never change (renaming keeps history). A new exercise's or metric's id is a slug of its name, de-duplicated (`waist`, then `waist_2`). | Library, Measurements |

---

## 5. Precondition recipes

UI-only steps, so a manual or agent tester can set up state without code. The automated suite reaches the same states
through the repositories in each test's `arrange()`.

| Recipe | Steps |
|---|---|
| **R0 Fresh install** | `adb shell pm clear com.example.mytrackerapp`, then launch the app. Expect `WEEK 1 · DAY 1`. |
| **R1 Warm-up done** | Today → `WARM UP, THEN CIRCUIT 1` → `SKIP WARM-UP`. Expect CTA `START CIRCUIT 1`. |
| **R2 List mode** | `[desc: Settings]` → tap `Guided mode by default` (switch turns off) → `[desc: Back]`. |
| **R3 Complete circuit N** | R2, then Today → tap card `Circuit N` → `✓ COMPLETE ALL (13 LEFT)` → `COMPLETE ALL` → `[desc: Close circuit]`. |
| **R4 Finish day 1** | R3 for circuits 1, 2, 3 and 4. |
| **R5 Finish the cycle** | On Today, repeat until `CYCLE COMPLETE` shows: `[desc: End day early]` → `END DAY`. If `SKIP STRETCHING` shows, tap it. Takes 24 rounds from a fresh install. |
| **R6 Squat logs reps + load** | LIBRARY → `Squat` → `[desc: Edit exercise]` → tap `Log reps` → tap `Log load` → set `Default load (kg)` to `10` → `SAVE`. |
| **R7 Units in lb / in** | Settings → `Program rules` → tap `LB` and `IN` → `SAVE FOR NEXT CYCLE`. |

---

## 6. Test cases by feature

### NAV: navigation and app shell

The app is dark-only and opens on Today. The bottom bar has four tabs. Every other screen is full-screen, with a back or close control.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| NAV-01 | P0 | R0 | Tap `PROGRAM`, `PROGRESS`, `LIBRARY`, `TODAY` in turn | The headers read `Program` (subtitle `4 weeks · 6 days a week · 132 circuits`), `Progress` (`Current cycle`), `Library` (`29 moves`), then `WEEK 1 · DAY 1` | NavigationE2eTest.nav01 |
| NAV-02 | P1 | R0 | `[desc: Settings]`, then `[desc: Back]` | Settings has no bottom bar. Back returns to Today with the bar visible | NavigationE2eTest.nav02 |
| NAV-03 | P1 | R0 | Tap card `Circuit 1`, then press system Back | The circuit opens full-screen (`CIRCUIT 1 · WEEK 1 DAY 1`). Back returns to Today | NavigationE2eTest.nav03 |
| NAV-04 | P2 | R0 | LIBRARY → chip `Stretch` → TODAY → LIBRARY | Still filtered: `8 moves` | NavigationE2eTest.nav04 |
| NAV-05 | P2 | any | Rotate the device on each screen | No crash. Screen state is kept (the guided position is saveable) | — |

### TOD: Today screen

Today answers "what do I still owe today?". It shows a header (`WEEK w · DAY d`, `Today`,
`<n> circuits · <m> exercises each`), a ring (`[desc: <done> of <n> circuits complete]`) with `<x> exercises done` and
`<y> left today`, the warm-up card, a `CIRCUITS` list with cards (`Circuit i`, `done/13`), the stretch card, and one
sticky call-to-action (CTA). The active circuit card has a lime border; finished cards show a filled tick.

**CTA logic, in order:** there is an unfinished circuit and warm-up is pending → `WARM UP, THEN CIRCUIT n` (opens the
warm-up). There is an unfinished circuit → `START CIRCUIT n`. All circuits are done and stretch is pending →
`FINISH WITH STRETCHING` plus `SKIP STRETCHING`. Otherwise → `DAY COMPLETE` (disabled). If warm-up or stretch is
turned off in the rules, its card disappears and it never gates the CTA.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| TOD-01 | P0 | R0 | Open the app | `WEEK 1 · DAY 1`, `4 circuits · 13 exercises each`, `[desc: 0 of 4 circuits complete]`, `52` left today, `8 moves · before your first circuit`, cards `Circuit 1`–`Circuit 4` each `0/13`, `8 stretches · after your last circuit` (dimmed), CTA `WARM UP, THEN CIRCUIT 1` | TodayE2eTest.tod01 |
| TOD-02 | P0 | R0 | CTA → `SKIP WARM-UP` | Back on Today: `8 moves · done`, the warm-up box is ticked, CTA `START CIRCUIT 1` | TodayE2eTest.tod02 |
| TOD-03 | P0 | R1 | CTA `START CIRCUIT 1` | Circuit 1 opens on `Squat` | TodayE2eTest.tod03 |
| TOD-04 | P0 | R0 | `[desc: End day early]` → read the dialog → `KEEP GOING`; again → `END DAY` | Dialog `End day early?` with body starting `Circuits 1, 2, 3, 4 will stay incomplete, and you'll move on to the next day. Your finished work is kept.`. Keep going changes nothing. End day moves to `WEEK 1 · DAY 2` | TodayE2eTest.tod04 |
| TOD-05 | P1 | R0 | Tap card `Circuit 2` → `✓  DONE` → `[desc: Close circuit]` | Circuit 2 card shows `1/13`, `51` left today, ring still `0 of 4` | TodayE2eTest.tod05 |
| TOD-06 | P0 | R1 + R4 | Look at Today after the last circuit | Still `WEEK 1 · DAY 1` (the day isn't settled until stretched), `[desc: 4 of 4 circuits complete]`, `8 stretches · finish your day`, CTA `FINISH WITH STRETCHING` and `SKIP STRETCHING`. Program's current-day outline is also still on W1 D1 | TodayStretchHoldE2eTest.tod06, StretchHoldTest, FullCycleTest |
| TOD-07 | P0 | as TOD-06 | `SKIP STRETCHING` | Moves to `WEEK 1 · DAY 2`, CTA `WARM UP, THEN CIRCUIT 1` | TodayStretchHoldE2eTest.tod07 |
| TOD-08 | P0 | as TOD-06 | `FINISH WITH STRETCHING` → `SKIP STRETCH` | The stretch screen reads `STRETCH · WEEK 1 DAY 1` (not day 2). Afterwards Today shows `WEEK 1 · DAY 2` | TodayStretchHoldE2eTest.tod08 |
| TOD-09 | P1 | R0, Rules: warm-up off, stretch off, APPLY | Look at Today | No `WARM-UP` or `STRETCH` cards. CTA `START CIRCUIT 1` | TodayRoutinesOffE2eTest.tod09 |
| TOD-10 | P2 | R1 + R4 | Program tab → open W1 D2 `Circuit 1` in list mode, tick one exercise → back to Today | Today still shows `WEEK 1 · DAY 1` with `FINISH WITH STRETCHING`. Work on a future day never skips the unstretched one | StretchHoldTest.tickingTheNextDayFromTheListDoesNotSkipTheStretch |
| TOD-11 | P2 | R0 | End day 1 early with 2 circuits done | Not held for stretch. Goes straight to `WEEK 1 · DAY 2` | StretchHoldTest.aDayEndedEarlyIsNotHeld |
| TOD-12 | P2 | R0 | Advance to week 4 (R5 partially: end 18 days early) | `7 circuits · 13 exercises each`, all 7 cards reachable by scrolling, and the CTA stays visible (sticky) | — |

### RTN: warm-up and stretch routines

Routines use the same screen as circuits and follow the same guided or list preference. They always belong to
**Today's day**. In guided mode, reaching the end, or tapping `SKIP WARM-UP` / `SKIP STRETCH`, sets the day's flag. In
list mode, ticking every move sets the flag automatically, and `DONE WITH WARM-UP` / `DONE WITH STRETCH` sets it early.
The flag means "I warmed up". It is not a per-move tally.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| RTN-01 | P0 | R0 | CTA → warm-up | Guided: overline `WARM-UP · WEEK 1 DAY 1`, `1/8`, `Neck Rolls`, target `5` `REPS`, buttons `✓  DONE`, `SKIP`, `⇄ CHECKLIST`, `SKIP WARM-UP` | TodayE2eTest.tod02 |
| RTN-02 | P1 | R0 | Warm-up guided: `✓  DONE` ×8 | Returns to Today with `8 moves · done` | — |
| RTN-03 | P1 | R2, end day 1 early | Tap the `WARM-UP` card → `✓ COMPLETE ALL (8 LEFT)` → `COMPLETE ALL` → close | Title `WARM-UP`. After closing, Today shows `8 moves · done` | ChecklistE2eTest.lst07 |
| RTN-04 | P1 | R2, end day 1 early | CTA → `DONE WITH WARM-UP` | Returns to Today with `8 moves · done`, CTA `START CIRCUIT 1` | ChecklistE2eTest.lst08 |
| RTN-05 | P1 | R0 | Warm-up: tick 3 moves in list mode → close → reopen | The 3 ticks are kept (a half-done routine resumes) | RoutineRepositoryTest.aHalfDoneRoutineResumesWhereItLeftOff |
| RTN-06 | P0 | R0 | Warm-up done, then look at Progress | Warm-up completions add nothing: `0%`, `EX. DONE` `0` | RoutineRepositoryTest.warmUpCompletionsNeverCountTowardTheDayTotal |
| RTN-07 | P2 | R0 | Library → Neck Rolls → Edit → switch `Enabled` off → SAVE → open warm-up | Warm-up shows 7 moves, without Neck Rolls | CircuitCompositionTest.aDisabledWarmUpMoveLeavesTheRoutine |

### GUI: guided circuit (default mode)

Header: `[desc: Close circuit]`, overline `CIRCUIT c · WEEK w DAY d`, position `i/13`, and a segment bar (lime = done).
The body shows the category chip, the name, muscles, a `SIDE 1` / `SIDE 1 DONE` / `SIDE 2` badge for per-side exercises,
then either a target circle (`5` `REPS`) or a timer dial. On side 1 of a per-side exercise the primary button reads `DONE · SIDE 1` rather than `✓  DONE`. Below that is a `HOW TO` card with the instructions and
`WATCH FORM VIDEO`. The pager opens on the **first unticked** exercise, and `✓  DONE` / `SKIP` jump to the next **unticked** one, so exercises ticked in list mode are never offered again.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| GUI-01 | P0 | R1 | `START CIRCUIT 1` | `1/13`, `Squat`, chip `BODYWEIGHT`, `5` `REPS`, `HOW TO`, `WATCH FORM VIDEO`, buttons `✓  DONE`, `SKIP`, `⇄ CHECKLIST` | GuidedCircuitE2eTest.gui01 |
| GUI-02 | P0 | R1 | `✓  DONE` → close | Advances to `2/13` `Push-up`. Today: Circuit 1 `1/13`, `51` left | GuidedCircuitE2eTest.gui02 |
| GUI-03 | P1 | R1 | `SKIP` → close | Advances to `2/13`. Nothing recorded: Circuit 1 `0/13` | GuidedCircuitE2eTest.gui03 |
| GUI-04 | P1 | R1 | `SKIP` ×13 → dialog → `BACK TO MISSED`; then `SKIP` ×13 → `FINISH ANYWAY` | Dialog `0 of 13 done`, `You skipped 13 exercises. You can go back to them now, or leave this circuit partial and pick it up later.` Back to missed returns to `1/13` Squat. Finish anyway returns to Today | GuidedCircuitE2eTest.gui05 |
| GUI-05 | P0 | R1, 12 of 13 done in circuit 1 | `START CIRCUIT 1` → `✓  DONE` | Opens on `13/13` `Band Row`. Done closes the circuit by itself, and Today shows `[desc: 1 of 4 circuits complete]` | GuidedCircuitArrangedE2eTest.gui07 |
| GUI-06 | P1 | R1 | `⇄ CHECKLIST` → close → open card `Circuit 2` | List mode opens for circuit 2 too. Settings: `Guided mode by default` is off (switching mid-circuit changes the default) | GuidedCircuitE2eTest.gui06 |
| GUI-07 | P0 | R0 | PROGRAM → day square `[desc: Week 1 day 2, not started]` → `Circuit 1` | `CIRCUIT 1 · WEEK 1 DAY 2`, banner `Preview — finish the current day before training this one.`, `✓  DONE` disabled. `SKIP` still browses | LockedDayGuidedE2eTest.gui09 |
| GUI-08 | P1 | R2, circuit 1 | Tap the name `External Rotation` → `DONE · SIDE 1` → `CONTINUE · SIDE 2` → `✓  DONE` | Badge `SIDE 1` (button `DONE · SIDE 1`), then `SIDE 1 DONE` with `SWITCH SIDES`, then `SIDE 2` (button `✓  DONE`). **One** completion is written at the end, and you return to the list | ChecklistE2eTest.lst04 |
| GUI-09 | P2 | R1 | Tap `WATCH FORM VIDEO` on Squat | YouTube or the browser opens a search for `Squat` (see MAN-01) | — |
| GUI-10 | P2 | R1 | Rotate the device mid-circuit | Stays on the same exercise and stage | — |
| GUI-11 | P1 | R1 + R2, circuit 1: tick `Push-up` and `Dead Hang` in the list | `⇄ GUIDED` (opens on `Squat`) → `SKIP` | Jumps from `1/13` Squat straight to `4/13` `Crunch`, skipping the two ticked exercises | NextUndoneIndexTest (JVM) |

### TMR: hold timer (timed exercises)

The dial counts down from the week's target (`15` `OF 15 SEC`). It runs on the monotonic clock, so it stays correct
across backgrounding and calls. Accessibility announcements are `15 second hold, not started`, `15 second hold`,
`10 seconds left`, `5 seconds left` and `Hold complete`.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| TMR-01 | P0 | R1, circuit 1 on `Dead Hang` (`SKIP` ×2) | `START · 15 SEC` → `❚❚ PAUSE` → `RESET` | Running shows `❚❚ PAUSE` and `RESET`. Paused shows `RESET` and `RESUME`. Reset returns to `START · 15 SEC` | GuidedCircuitE2eTest.gui04 |
| TMR-02 | P0 | as TMR-01, `Timer auto-advance` on | Start and wait out the hold | At 0 the dial turns lime. About 0.7 s later it records the set and advances to `4/13` `Crunch` | GuidedCircuitArrangedE2eTest.gui08 (2 s hold) |
| TMR-03 | P1 | `Timer auto-advance` off | Wait out the hold | It stays on the exercise with `✓  DONE`. Tap to record | — |
| TMR-04 | P2 | `Sound cues` on | Run a hold | A short beep at 3, 2 and 1, and a longer tone at 0 (MAN-03) | — |
| TMR-05 | P2 | — | Start a hold, background the app for 5 s, return | The remaining time accounts for the time away | HoldTimerTest (JVM) |
| TMR-06 | P2 | Rules unchanged; Library → Dead Hang → Edit: `Progression step per week` 5 → SAVE | Open a week 2 circuit's Dead Hang | Target `20` (15 + 5 × (2−1)) | TargetForWeekTest (JVM) |

### LST: list (checklist) mode

Header: `[desc: Close circuit]`, title (`CIRCUIT c` / `WARM-UP` / `STRETCH`), `⇄ GUIDED`, a big `done` `/13` and a
progress bar. Rows are grouped under `BODYWEIGHT · 6` and `RESISTANCE BAND · 7` (routines: `WARM-UP · 8` /
`STRETCH · 8`). Each row has three tap zones: the checkbox `[desc: Mark <name> done]`, the name (runs that one exercise
in guided mode, with `BACK TO LIST`), and the play icon `[desc: Watch <name> video]`. The sticky bar shows
`✓ COMPLETE ALL (N LEFT)` while anything is left.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| LST-01 | P0 | R1 + R2 | `START CIRCUIT 1` | `CIRCUIT 1`, `/13`, sections as above, `Squat` with badge `5 reps`, `[desc: Watch Squat video]`, `✓ COMPLETE ALL (13 LEFT)`, `⇄ GUIDED` | ChecklistE2eTest.lst01 |
| LST-02 | P0 | R1 + R2 | Tap `[desc: Mark Squat done]` twice | Checked → `(12 LEFT)`. Unchecked → `(13 LEFT)`. A done name is struck through | ChecklistE2eTest.lst02 |
| LST-03 | P0 | R1 + R2 | `✓ COMPLETE ALL (13 LEFT)` → `CANCEL`; again → `COMPLETE ALL` → close | Dialog `Complete all?` / `Mark the 13 remaining exercises done. Rep/load logging is skipped for these.` Cancel changes nothing. Afterwards the bar disappears, and Today shows `1 of 4` and `13/13` | ChecklistE2eTest.lst03 |
| LST-04 | P1 | R1 + R2 | Tap the name `Push-up` → `BACK TO LIST`; tap `Crunch` → system Back | Both return to the list with nothing recorded | ChecklistE2eTest.lst05 |
| LST-05 | P1 | R1 + R2 | `⇄ GUIDED` | Guided pager for the same circuit, and guided becomes the default | ChecklistE2eTest.lst06 |
| LST-06 | P0 | R2 | PROGRAM → `[desc: Week 1 day 3, not started]` → `Circuit 1` → tick Squat → close | Note `Future day — ticks here count for week 1 day 3.`. The tick is allowed. Program then reads `[desc: Week 1 day 3, 1 of 52]` | ChecklistFutureDayE2eTest.lst09 |
| LST-07 | P2 | R2 | Double-tap a checkbox quickly | Ends checked or unchecked, never counted twice | DaoTest.doubleTapDoesNotDuplicateACompletion |
| LST-08 | P2 | `Haptics` on | Tick a row | Vibrates (MAN-04) | — |

### LOG: rep and load logging

An exercise with **Log reps** and/or **Log load** turned on opens a bottom sheet when it's ticked, instead of ticking
straight away. The sheet shows the name, `Reps` (pre-filled with the last logged reps, or the week's target),
`Load (<kg|lb>)` (pre-filled with the last load, or the default load), `Band` (only when a default band is set), `RPE`
chips `1`–`10` (tap again to clear), `Note`, and the buttons `SKIP` and `SAVE`. **Skipping, or swiping the sheet away,
still ticks the exercise with no detail.** Logging never blocks finishing. `COMPLETE ALL` skips the sheet.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| LOG-01 | P0 | R1 + R2 + R6, Push-up: Log reps on | Tick Squat | The sheet opens pre-filled: `Reps` = `5`, `Load (kg)` = `10` | SetLoggingE2eTest.log01 |
| LOG-02 | P0 | as LOG-01 | Reps `7`, RPE `8` → `SAVE` → close → LIBRARY → Squat | The row is ticked. Detail `PERFORMANCE`: `10.0 kg` (`BEST`), `7` (`BEST REPS`), `70 kg` (`TOTAL VOLUME`), and `VOLUME BY TRAINING DAY` bars | SetLoggingE2eTest.log02 |
| LOG-03 | P1 | as LOG-01 | Log Squat reps `9` and load `12.5` in circuit 1, then tick Squat in circuit 2 | The second sheet is pre-filled with `9` and `12.5` | SetLoggingE2eTest.log03 |
| LOG-04 | P0 | as LOG-01 | Tick Push-up → `SKIP` | Ticked, `(12 LEFT)` | SetLoggingE2eTest.log04 |
| LOG-05 | P1 | as LOG-01 | Tick Crunch (untracked) | Ticks at once, no sheet | SetLoggingE2eTest.log05 |
| LOG-06 | P1 | as LOG-01 | `⇄ GUIDED` → `✓  DONE` on Squat → `SAVE` | The sheet opens before advancing. After Save it moves to `2/13 Push-up` | SetLoggingE2eTest.log06 |
| LOG-07 | P1 | R0 | Tick Crunch in any circuit → LIBRARY → Crunch | `No sets logged yet. Turn on rep or load logging in the exercise editor.` | SetLoggingE2eTest.log07 |
| LOG-08 | P1 | R6 + R7 (lb) | Tick Squat → check the load → reps `5`, load `22` → SAVE → Library → Squat | Field `Load (lb)` pre-filled `22.0` (10 kg). Detail shows `22.0 lb` and `110 lb` | SetLoggingPoundsE2eTest.log08 |
| LOG-09 | P2 | as LOG-01 | Untick Squat, then tick it again | The old detail is gone. The new sheet pre-fills from earlier logged sets (or defaults) | SetDetailTest.unTickingAndRetickingLosesTheOldDetail |

### PRG: Program tab

Title `Program`, subtitle `<W> weeks · <D> days a week · <C> circuits`. There's one card per week: `WEEK w` (lime on the
current week), `<n> circuits/day · <D> days`, circuits done out of the week total, and a row of day squares (lime =
complete, dim green = partial, grey = not started, lime outline = current day). Tapping a square expands it:
`Day d · <done>/<total> exercises`, or for future days `Day d · preview — finish the current day first`, followed by
its circuit cards (dimmed when locked). Tap the square again to collapse.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| PRG-01 | P0 | R0 + circuit 1 done | Open PROGRAM | `4 weeks · 6 days a week · 132 circuits`. `WEEK 1`, `4 circuits/day · 6 days`, `1/24` … `WEEK 4`, `7 circuits/day · 6 days`, `0/42` | ProgramE2eTest.prg01 |
| PRG-02 | P1 | as PRG-01 | Read the square descriptions | `[desc: Week 1 day 1, 13 of 52]`, `[desc: Week 1 day 2, not started]` | ProgramE2eTest.prg02 |
| PRG-03 | P0 | as PRG-01 | Tap W1 D1 → `Circuit 2` | `Day 1 · 13/52 exercises`, Circuit 1 `13/13`. Opens `CIRCUIT 2 · WEEK 1 DAY 1` | ProgramE2eTest.prg03 |
| PRG-04 | P2 | as PRG-01 | Tap W1 D1 twice | Expands, then collapses | ProgramE2eTest.prg04 |
| PRG-05 | P1 | R0 | Tap W2 D1 | `Day 1 · preview — finish the current day first` | ProgramE2eTest.prg05 |
| PRG-06 | P1 | as PRG-01 | End day 1 early → PROGRAM | `[desc: Week 1 day 1, ended early, 13 of 52]` | ProgramE2eTest.prg06 |
| PRG-07 | P1 | Rules: 2 weeks, 3 days, circuits 2 & 3, APPLY | Open PROGRAM | `2 weeks · 3 days a week · 15 circuits`, `2 circuits/day · 3 days`, `3 circuits/day · 3 days`, and no `WEEK 3` | ProgramCustomRulesE2eTest.prg07 |
| PRG-08 | P1 | as PRG-07 + `Lock future days` off | Tap W2 D3 | `Day 3 · 0/39 exercises`. The word "preview" doesn't appear | ProgramCustomRulesE2eTest.prg08 |

### PRS: Progress tab

Title `Progress` / `Current cycle`. Tiles: `STREAK` (consecutive training dates ending today or yesterday), `CYCLE`
(percent), `EX. DONE`. Then the `<W>-WEEK MAP` heat grid (`D1…D6` × `W1…W4`, cells `[desc: Week w day d, <state>]`,
legend `Complete` / `Partial` / `Not yet`), `CIRCUITS PER WEEK` bars (`Week w · n/day` and `done/total`), `HEALTH` with a
`Body measurements` row, and `MOST DONE` (the top 3 names joined with ` · `, plus `<n> completions`, or
`Complete your first circuit to start a streak.`).

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| PRS-01 | P0 | R0 | Open PROGRESS | `0`, `0%`, `0`, `4-WEEK MAP`, `[desc: Week 1 day 1, not started]`, `Week 1 · 4/day` `0/24`, `Complete your first circuit to start a streak.` | ProgressEmptyE2eTest.prs01 |
| PRS-02 | P0 | Day 1 done + stretch skipped + 1 circuit on day 2 | Open PROGRESS | `3%` (65/1716), `65`, `[desc: Week 1 day 1, complete]`, `[desc: Week 1 day 2, 13 of 52]`, `5/24`, `Band Row · Bicep Curl · Crunch`, `5 completions` | ProgressWithWorkE2eTest.prs03 |
| PRS-03 | P1 | R0 | Tap `Body measurements` | Opens Body measurements (`DERIVED`, `BMI`) | ProgressEmptyE2eTest.prs02 |
| PRS-04 | P1 | Train on 2 consecutive days | Open PROGRESS | `STREAK` `2`. It stays alive through the next day, and reads `0` once a whole calendar day is missed | StreakTest (JVM) |
| PRS-05 | P2 | Complete whole circuits only (every exercise ties) | Open PROGRESS | Ties break by exercise id, so it's always `Band Row · Bicep Curl · Crunch`, and stable across launches | ProgressWithWorkE2eTest.prs03, ProgramStatsRepositoryTest |

### LIB: Library and exercise detail

Title `Library`, `<n> moves` (or `1 move`), `[desc: Add exercise]` (+), a search field (`Search exercises or muscles`;
it matches the name or muscles, case-insensitive), and chips `All` / `Program` / `Warm-up` / `Stretch` / `Archived`.
Rows show a colour bar, the name, muscles and a target badge. **Exercise detail:** `[desc: Back]`,
`[desc: Edit exercise]`, an `ARCHIVED — kept for history, no longer in the rotation` banner when archived, a category
chip, name, muscles, target plaque (`5` `REPS`, plus `EACH SIDE`), `HOW TO` and the instructions,
`Watch form video ↗`, then `YOUR HISTORY` (`<n>` ` completions this cycle`, a sparkline, `LAST <n> TRAINING DAY(S)`, or
`Not done yet this cycle.`), then `PERFORMANCE` (see LOG).

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| LIB-01 | P0 | R0 | Open LIBRARY | `29 moves`, sections `BODYWEIGHT · 6`, `RESISTANCE BAND · 7`, `WARM-UP · 8`, `STRETCH · 8`, Squat muscles `Legs · Glutes · Core` | LibraryE2eTest.lib01 |
| LIB-02 | P0 | R0 | Chips `Program`, `Warm-up`, `Stretch`, `Archived`, `All` | `13 moves`, `8 moves`, `8 moves`, `0 moves` + `Nothing here yet.`, `29 moves` | LibraryE2eTest.lib02 |
| LIB-03 | P1 | R0 | Search `glute`, then `zzz` | Shows Glute Bridge and Squat (muscles match), then `No exercise matches “zzz”.` | LibraryE2eTest.lib03 |
| LIB-04 | P0 | R0 | Tap `Squat` → `[desc: Back]` | `BODYWEIGHT`, `Squat`, `REPS`, `HOW TO`, `Watch form video`, `YOUR HISTORY`, `Not done yet this cycle.`. Back returns to the list | LibraryE2eTest.lib04 |
| LIB-05 | P2 | R0 | Tap `External Rotation` | The plaque shows `EACH SIDE` | LibraryE2eTest.lib05 |
| LIB-06 | P1 | Circuits 1 and 2 done | Tap `Squat` | `2`, ` completions this cycle`, `LAST 1 TRAINING DAY`, `PERFORMANCE` with the no-sets hint | LibraryHistoryE2eTest.lib06 |
| LIB-07 | P2 | R0 | Tap `Watch form video` on a device with no browser | Snackbar `No app can open <url>` | — |

### CAT: exercise editor (add, edit, archive, restore)

Open it from Library `[desc: Add exercise]` (`New exercise`) or from detail `[desc: Edit exercise]`
(`Edit exercise`). The fields: `Name`; `SLOT` chips `PROGRAM` / `WARMUP` / `STRETCH` (this decides which circuit or
routine it joins); `CATEGORY` chips `BODYWEIGHT` / `BAND` / `WARMUP` / `STRETCH` (display only); `Muscles`;
`Instructions`; `TARGET` chips `REPS` / `SECONDS` with the stepper `Target value` (1–999); the switch `Per side`;
`Target label`; the stepper `Progression step per week` (0–50); `TRACKING` switches `Log reps` and `Log load` (Log load
reveals `Default load (kg)` and `Default band`); `Video URL` (hint `Leave blank to search YouTube for the exercise name.`);
and `ROTATION` switch `Enabled`. For PROGRAM slot exercises the note
`Changing a program exercise changes the draft's exercises-per-circuit. Applies to the current cycle only after you apply rules.`
shows. The actions are `Archive` / `Restore` (existing exercises) and `SAVE`, which is disabled while there are errors.

Validation messages: `Name can't be empty.`, `Name must be 60 characters or fewer.`, `Target must be between 1 and 999.`,
`Video URL must start with http:// or https://, or be left blank.`, `Progression step must be between 0 and 50.`

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| CAT-01 | P0 | R0 | + → check → Name `Pistol Squat` → `SAVE` → chip `Program` | Starts with `Name can't be empty.` and SAVE disabled. After saving: `30 moves`, then Program `14 moves` including `Pistol Squat` | CatalogEditorE2eTest.cat01 |
| CAT-02 | P1 | R0 | + → Name → `Video URL` `youtube.com/watch` | The URL error shows and SAVE is disabled | CatalogEditorE2eTest.cat02 |
| CAT-03 | P0 | R0 | Squat → edit → Name `Air Squat` → SAVE → Back | The detail and the Library show `Air Squat`, still `29 moves`, and history is kept | CatalogEditorE2eTest.cat03, CatalogCrudTest |
| CAT-04 | P0 | R0 | Squat → edit → `Archive` → `CANCEL`; again → `ARCHIVE` → Back → chip `Archived` | Dialog `Archive this exercise?`. After archiving, the detail shows the ARCHIVED banner, the Library shows `28 moves`, and Archived shows `1 move` / `Squat` | CatalogEditorE2eTest.cat04 |
| CAT-05 | P0 | as CAT-04 | Archived Squat → edit (shows `ARCHIVED`) → `Restore` → Back | The banner is gone. `29 moves` | CatalogEditorE2eTest.cat05 |
| CAT-06 | P1 | R0 | Band Row → edit → tap `Log load` | The `Default load (kg)` and `Default band` fields appear | CatalogEditorE2eTest.cat06 |
| CAT-07 | P1 | R0 | + → slot `WARMUP` | The program-exercise note disappears | CatalogEditorE2eTest.cat07 |
| CAT-08 | P0 | Mid-cycle | Archive Squat → open any circuit | **Still 13 exercises, including Squat.** The circuit can be completed. After Rules → `APPLY TO CURRENT CYCLE`, circuits have 12 | CircuitCompositionTest.archivingMidCycleKeepsTheCircuitCompletable |
| CAT-09 | P1 | Mid-cycle | Add a program exercise → open a circuit | Not in the running cycle (still 13) until it's applied. After applying: 14 | CircuitCompositionTest.anExerciseAddedMidCycleWaitsForApply |
| CAT-10 | P1 | 12 of 13 program exercises archived | Edit the last one: switch `Enabled` off (or move it to slot WARMUP) → SAVE | Refused with snackbar `At least one program exercise must stay enabled.` | CatalogCrudTest.theEditorCannotSwitchOffOrReslotTheLastProgramExercise |
| CAT-11 | P2 | R0 | Clear an exercise's Video URL → SAVE → watch video | Opens a YouTube search for the current name. The search follows later renames | CatalogCrudTest.blankVideoUrlFollowsTheName |

### RUL: program rules editor

Settings → `Program rules`. Title `Program rules`, subtitle `<W> weeks · <D> days · <E> exercises a circuit`.
`SHAPE` steppers `Weeks` (1–26) and `Days per week` (1–7). `CIRCUITS A DAY` has one stepper per week, `Week w` (1–20);
adding a week copies the last week's value. `ROUTINES` switches: `Warm-up enabled` (`<n> moves before your first circuit`)
and `Stretch enabled` (`<n> stretches after your last circuit`). `COUNTING` switches: `Count warm-up and stretch in totals`,
`Lock future days`, plus the stepper `Day rollover hour` (0–23, shown as `4:00`). `UNITS` toggles: Weight `KG` / `LB`,
Length `CM` / `IN` (display only). `IMPACT` shows `<old> → <new> exercises · <days> days`, with loss lines
`<n> day(s) would reopen.` and `<n> completion(s) would stop counting (nothing is deleted).`. Last comes the
`Restore default rules` row (`Back to 4 weeks · 6 days · 13 exercises a circuit`). The sticky buttons are
`APPLY TO CURRENT CYCLE` and `SAVE FOR NEXT CYCLE`; both are disabled while there are errors. Steppers use
`[desc: Increase <label>]` and `[desc: Decrease <label>]`.

Validation messages: `Weeks must be between 1 and 26.`, `Days per week must be between 1 and 7.`,
`Every week needs between 1 and 20 circuits a day.`,
`A circuit needs between 1 and 40 exercises. Turn at least one program exercise back on.`,
`Warm-up and stretch are both off, so there is nothing for "count them in totals" to count.`

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| RUL-01 | P0 | R0 | Open Program rules | `4 weeks · 6 days · 13 exercises a circuit`, `8 moves before your first circuit`, `8 stretches after your last circuit`, `1716 → 1716 exercises · 24 days` | RulesE2eTest.rul01 |
| RUL-02 | P0 | R0 | `[desc: Increase Week 1]` → `SAVE FOR NEXT CYCLE` → back to Today | Impact `1716 → 1794 exercises · 24 days`, snackbar `Saved for the next cycle.`. Today **still** `4 circuits` | RulesE2eTest.rul02 |
| RUL-03 | P0 | R0 | Increase Week 1 → `APPLY TO CURRENT CYCLE` → `CANCEL`; again → `APPLY` → Today | Dialog `Apply to the current cycle?` … `This change is lossless.`. Snackbar `Applied to the current cycle.`. Today `5 circuits · 13 exercises each` | RulesE2eTest.rul03 |
| RUL-04 | P1 | R0 | `[desc: Increase Weeks]` | `5 weeks · …`, a new `Week 5` stepper (7 circuits), `1716 → 2262 exercises · 30 days` | RulesE2eTest.rul04 |
| RUL-05 | P1 | R0 | Turn on `Count warm-up and stretch in totals`, then turn off both routines | The count-them error shows. Both buttons are disabled | RulesE2eTest.rul05 |
| RUL-06 | P1 | R0 | Increase Weeks → SAVE → `Restore default rules` → `RESTORE` | Dialog `Restore default rules?`. The header is back to `4 weeks · 6 days …`. The running cycle isn't touched | RulesE2eTest.rul06 |
| RUL-07 | P1 | R0 | Increase Days per week → SAVE → Back → reopen | `4 weeks · 7 days · 13 exercises a circuit` (the draft persists) | RulesE2eTest.rul07 |
| RUL-08 | P1 | R0 | `LB`, `IN` → SAVE → Body measurements → log sheet | The fields read `Body weight (lb)` and `Waist (in)` | RulesE2eTest.rul08 |
| RUL-09 | P0 | Circuit 4 of W1 D1 complete | `[desc: Decrease Week 1]` → APPLY → Today | Impact and dialog say `13 completion(s) would stop counting`. Today `3 circuits`, no `Circuit 4`. The rows are kept: re-increase and apply, and they count again | RulesLossyApplyE2eTest.rul09, OrphanFilterTest |
| RUL-10 | P1 | R0 | Turn off `Lock future days` → APPLY → open a future circuit in guided mode | No preview banner. `✓  DONE` is enabled | InvariantTogglesTest.turningOffLockFutureDaysMakesEveryDayEditable |
| RUL-11 | P1 | R0 | Turn on `Count warm-up and stretch in totals` → APPLY → finish the warm-up → PROGRAM → tap W1 D1 | The Program day total becomes `68` (52 + 8 + 8), and the warm-up ticks count toward it (`Day 1 · 8/68 exercises`). Today's circuit counts are unchanged | InvariantTogglesTest.countingRoutinesAddsWarmUpToTheDayTotal |
| RUL-12 | P2 | R0 | Change a rule and press Back without saving | The change is discarded, with no prompt (see KI-07) | — |

### SET: settings

Settings sections: `PROGRAM` (`Program rules`), `HEALTH` (`Body measurements`), `SESSION` switches
(`Guided mode by default`, `Timer auto-advance`, `Keep screen awake`), `FEEDBACK` switches (`Sound cues`, `Haptics`),
and `YOUR DATA` (`Export training data`, `Reset current cycle`). All switches default to **on** and persist across restarts.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| SET-01 | P1 | R0 | Open Settings | Every row above is present | SettingsE2eTest.set01 |
| SET-02 | P0 | R0 | Turn `Haptics` and `Timer auto-advance` off → kill and relaunch the app → Settings | Both are still off | SettingsE2eTest.set02 |
| SET-03 | P0 | R0 | Turn `Guided mode by default` off → open a circuit | List mode | SettingsE2eTest.set03 |
| SET-04 | P2 | `Keep screen awake` on | Leave a guided circuit open for longer than the screen timeout | The screen stays on (MAN-05) | — |

### DAT: your data (export and reset)

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| DAT-01 | P0 | Circuit 1 done | Settings → `Reset current cycle` → `CANCEL`; again → `RESET CYCLE` → Back | Dialog `Reset this cycle?`. After the reset: `WEEK 1 · DAY 1`, `0 of 4`. Earlier cycles are untouched | SettingsResetE2eTest.set04, CycleRestartTest |
| DAT-02 | P1 | Some work done | `Export training data` → choose a location → save | The system file picker suggests `mytracker-export-<yyyy-mm-dd>.json`. Snackbar `Training data exported.`. The JSON has `exportedAt`, `schemaVersion` 1, and `cycles[]` with each cycle's `completions[]` (`week`, `day`, `circuit`, `exerciseId`, `completedAt`). See KI-01 for what's missing | CycleRestartTest.exportProducesParseableJsonCoveringEveryCycle (JSON only) |
| DAT-03 | P2 | — | Export → cancel the picker | Nothing happens, no snackbar | — |

### MEA: body measurements

Reached from Settings or Progress → `Body measurements`. The top bar has `[desc: Back]` and
`[desc: Log measurements]` (+). `DERIVED` tiles: `BMI`, `LEAN MASS`, `WAIST:HIP`, `WAIST:HEIGHT` (`—` until the
inputs exist, with the hint `Log weight, height, waist, hips and body fat to fill these in.`). `METRICS` lists one card
per enabled metric: the name, the latest value with its unit (or `—`), `vs last: ±x  ·  vs first: ±y`, and a sparkline
from two readings up. Then comes an `Edit metrics` row.

- **Log sheet:** `Log a measuring session`, `Date (yyyy-mm-dd)` (today by default), one field per enabled metric
  (`Body weight (kg)` … `Resting heart rate`, where unitless metrics have no brackets), `Note`, and the buttons `CANCEL`
  and `SAVE`. Every reading in one save shares the date and note.
- **Metric history:** the metric name is the title. Rows are newest first, with the value and unit, the date as
  `MMM d, yyyy`, and the note. Each row has `[desc: Edit reading]` (dialog `Edit reading`, fields `Value (<unit>)` and
  `Note`, buttons `SAVE` / `CANCEL`) and `[desc: Delete reading]` (dialog `Delete this reading?`, `DELETE`). Deleting is a
  **hard delete**. An empty history shows `No readings yet.`.
- **Edit metrics:** one switch row per metric (the name, with its hint as the subtitle). Custom metrics also get
  `ARCHIVE` (dialog `Archive <name>?`). An `ARCHIVED` section lists archived metrics. `+ ADD CUSTOM METRIC` opens the
  sheet `New metric`: `Name`, Kind `WEIGHT` / `LENGTH` / `PERCENT` / `COUNT`, `Decimals` (0–2), `Hint`, `ADD METRIC`.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| MEA-01 | P0 | R0 | Open Body measurements | All four derived tiles show `—` and the hint. 9 cards (see §3). `Neck` is absent | MeasurementsE2eTest.mea01 |
| MEA-02 | P0 | R0 | + → Body weight `80`, Height `180` → `SAVE` | The date is pre-filled with today. Cards show `80.0 kg` and `180 cm`. BMI `24.7` | MeasurementsE2eTest.mea02 |
| MEA-03 | P1 | R0 | + → Body weight `80` → `CANCEL` | Nothing is saved | MeasurementsE2eTest.mea03 |
| MEA-04 | P1 | R0 | `Edit metrics` → tap `Neck` → Back | A `Neck` card appears | MeasurementsE2eTest.mea04 |
| MEA-05 | P1 | R0 | Edit metrics → `+ ADD CUSTOM METRIC` → Name `Grip strength`, `COUNT` → `ADD METRIC` → `ARCHIVE` → confirm → Back | It's listed, then moves under `ARCHIVED`. It isn't on the measurements screen | MeasurementsE2eTest.mea05 |
| MEA-06 | P1 | 82.0 kg a week ago, 80.0 kg today | Open the screen | `80.0 kg`, `vs last: -2.0  ·  vs first: -2.0` | MeasurementHistoryE2eTest.mea06 |
| MEA-07 | P0 | as MEA-06 | Tap `Body weight` → edit the newest to `79.5` → SAVE → delete it → `DELETE` | `79.5 kg` shows, then it's gone. `82.0 kg` remains | MeasurementHistoryE2eTest.mea07 |
| MEA-08 | P1 | Weight 80, body fat 20, R7 | Open the screen | `176.4 lb`. Lean mass `141.1 lb` | MeasurementUnitsE2eTest.mea08 |
| MEA-09 | P1 | Weight 80, height 180, waist 90, hips 100, body fat 20 | Open the screen | BMI `24.7`, waist:hip `0.90`, waist:height `0.50`. The hint is gone | MeasurementUnitsE2eTest.mea09 |
| MEA-10 | P1 | R7 | Log Body weight `176.4` (lb) → switch back to KG | Shows `80.0 kg` (stored in kg, never converted) | MeasurementRepositoryTest.loggingInPoundsStoresKilograms |
| MEA-11 | P2 | R0 | Add a custom metric named `Waist` | A second `Waist` appears. The built-in Waist is unchanged (still cm, still feeds the ratios) | MeasurementRepositoryTest.aCustomMetricNamedLikeABuiltInDoesNotReplaceIt |
| MEA-12 | P2 | R0 | Log Body fat `150` | Not saved (percent must be 0–100). **No error is shown** (KI-02) | MeasurementRepositoryTest.loggingAnInvalidPercentFails |

### CYC: end of cycle

When every day is settled, Today shows `CYCLE COMPLETE`, `Nothing left to tick`,
`All <n> days of this cycle are done. Take a look at how it went, then start again whenever you're ready — your history is kept.`
and CTA `SEE CYCLE SUMMARY`. The **summary** shows `CYCLE COMPLETE`, a headline (`Four weeks done`; it follows the
program length), a ring `[desc: <p> percent of the cycle completed]` (`<p>%` / `OF THE PROGRAM`), and tiles `EXERCISES`,
`CIRCUITS`, `DAYS TRAINED`, `BEST STREAK` (the longest run, not the live one) and `DAYS ELAPSED`. The sentence is one of:
`Every one of <total> exercises. Nothing left on the table.`,
`<done> of <total> exercises, with <n> day(s) ended early. Finished is finished.`, or
`<done> of <total> exercises across <n> training days.`. The CTAs are `START A NEW CYCLE` and `NOT YET`.

| ID | P | Pre | Steps | Expected | Auto |
|---|---|---|---|---|---|
| CYC-01 | P0 | Day 1 done + stretch skipped, the other 23 days ended early | Open Today | The finished state with `All 24 days of this cycle are done` | CycleCompleteE2eTest.cyc01 |
| CYC-02 | P0 | as CYC-01 | `SEE CYCLE SUMMARY` | `Four weeks done`, `[desc: 3 percent of the cycle completed]`, `52`, the tiles above, and `… with 23 days ended early. Finished is finished.` | CycleCompleteE2eTest.cyc02 |
| CYC-03 | P1 | as CYC-01 | Summary → `NOT YET` | Back to the finished Today. The cycle isn't restarted | CycleCompleteE2eTest.cyc03 |
| CYC-04 | P0 | as CYC-01 | Summary → `START A NEW CYCLE` → PROGRESS | `WEEK 1 · DAY 1`, `0 of 4`, Progress `0%`. The old cycle is kept (and exported) | CycleCompleteE2eTest.cyc04, CycleRestartTest |
| CYC-05 | P1 | Finish the last day's circuits without stretching | Open Today | The last day stays current, waiting for its stretch, **before** the finished state appears | ProgramRulesTest (stretch-gate cases), FullCycleTest |
| CYC-06 | P2 | A new cycle, after editing the draft rules | Start a new cycle | The new cycle uses the draft (snapshotted at start) | RulesSnapshotTest.aFreshCycleIsSnapshottedFromTheDraftImmediately |

### INV: data invariants (automated; spot-check manually after risky changes)

| ID | P | Check | Auto |
|---|---|---|---|
| INV-01 | P0 | A whole cycle can be completed exercise by exercise, and the counter never skips or stalls | FullCycleTest.theWholeCycleCompletesAndTheCounterNeverSlips |
| INV-02 | P0 | Each day needs exactly its own number of exercises (52/65/78/91) | FullCycleTest.everyDayNeedsExactlyItsOwnNumberOfExercises |
| INV-03 | P0 | A finished cycle's stats add up (132 circuits, 1716 exercises) | FullCycleTest.finishedCycleStatsAddUp |
| INV-04 | P0 | Every database migration v1→v6 keeps the data | MigrationTest |
| INV-05 | P0 | The seed is exactly 29 exercises, the first cycle opens, and the rules are snapshotted | SeedTest, RulesSnapshotTest |
| INV-06 | P1 | Orphaned completions are kept but never counted | OrphanFilterTest |
| INV-07 | P1 | Rule validation and impact analysis | RuleValidationTest, RuleImpactTest (JVM) |

---

## 7. Smoke suite

Run on every build (about 15 minutes by hand; the automated equivalent is the full `e2e` package). The IDs:

`NAV-01, TOD-01, TOD-02, TOD-04, TOD-06, TOD-07, GUI-01, GUI-02, GUI-05, GUI-07, TMR-01, LST-01, LST-02, LST-03, LST-06,
LOG-01, LOG-02, LOG-04, PRG-01, PRG-03, PRS-01, LIB-01, LIB-04, CAT-01, CAT-04, CAT-08, RUL-02, RUL-03, RUL-09, SET-02,
DAT-01, MEA-02, MEA-07, CYC-02, CYC-04`

---

## 8. Manual-only checks

These depend on hardware, other apps or the system UI, so no automated test covers them.

| ID | Check | Expected |
|---|---|---|
| MAN-01 | Form video from guided mode, list mode and detail | YouTube or the browser opens. Linked videos for Push-up, Glute Bridge, Pull-Apart and Band Row; a name-only search for the rest |
| MAN-02 | Export to Drive or Downloads, then open the file | Valid, pretty-printed JSON (2-space indent) covering every cycle |
| MAN-03 | Sound cues with the volume up (the alarm stream) | Beeps at 3, 2 and 1, a distinct tone at 0. Silent when `Sound cues` is off |
| MAN-04 | Haptics | Vibrates on tick and in the last 3 s of a hold. Nothing when off |
| MAN-05 | Keep screen awake | The display doesn't sleep during a circuit. The normal timeout returns after leaving |
| MAN-06 | Day rollover | Tick a set at 01:00: it counts for the previous date in the streak, sparkline and volume. At 04:00 and later it counts for today (the date maths are covered by StreakTest) |
| MAN-07 | TalkBack pass on Today, a circuit and Program | Every icon, checkbox and day square is announced (for example `Mark Squat done, checkbox`) |
| MAN-08 | Upgrade install | Install the previous release, log data, install this build over it. All data is kept (INV-04) |
| MAN-09 | Launcher icon | The adaptive and legacy icons match |

---

## 9. Automated test inventory

| Suite | Location | What it covers |
|---|---|---|
| JVM unit (128) | `app/src/test` | `ProgramRulesTest` (shape, settling, **stretch gate**), `StreakTest`, `NextUndoneIndexTest`, `RuleValidationTest`, `RuleImpactTest`, `CatalogValidationTest`, `PerformanceStatsTest`, `BodyStatsTest`, `UnitsTest` (incl. locale), `RecentTalliesTest`, `ProgramTotalsTest`, `TargetForWeekTest`, `HoldTimerTest`, `LibraryFilterTest`, `VideoSearchTest`, `WeeksDoneHeadlineTest`, `WithUnitTest` |
| Data and repository (instrumented, 102) | `app/src/androidTest/.../data`, `.../repo` | DAO, migrations, seed, full-cycle walk, routines, stats, rules snapshot/apply/orphans/toggles, catalog CRUD and composition, set detail, measurements, stretch hold |
| **End-to-end UI** (89) | `app/src/androidTest/.../e2e` | Every screen and flow in §6. Test names start with the case ID (`tod06_…` = TOD-06) |

**How the UI suite works** (`E2eTest` base class):
- Before each test it clears and re-seeds the real database and resets every setting. Then it runs the test's
  `arrange()` (repository calls that reach a mid-program state quickly), then launches `MainActivity`.
- Assertions only read the screen. The helpers wait for asynchronous data (`see`, `dontSee`, `seeFieldValue`), scroll lazy
  lists automatically, and handle the upper-cased button labels (`tapButton("Save")` finds `SAVE`).
- Sound cues are turned off during the suite, so it's silent. Everything else runs at its defaults.

---

## 10. Known issues and limitations

These were found during this pass and deliberately **not** changed, because they're design decisions or missing
features rather than clear bugs. A regression run should confirm they're unchanged, and not report them as new.

| ID | Area | Issue |
|---|---|---|
| KI-01 | Export | The export only includes cycles and plain completions. It **omits** logged reps, load, RPE and notes, body measurements, custom metrics, custom exercises and rule snapshots, so it isn't a full backup. There's also no import. |
| KI-02 | Measurements | The log sheet silently drops invalid values (0, negative, percent over 100, non-numeric) with no message, and an unparseable date silently becomes "now". `SAVE` with no valid values does nothing. |
| KI-03 | Measurements | Archived metrics can't be restored. The `ARCHIVED` section only lists names. Metric name, kind and order can't be edited after creation. |
| KI-04 | Catalog | The repository supports reorder, duplicate and restoring the default catalog, but no screen exposes them. |
| KI-05 | Today | `DAY COMPLETE` (the disabled CTA) is effectively unreachable: once the stretch is done, the day releases and Today moves on. |
| KI-06 | — | Retired. Today and Program now always agree on the current day (see R-3 and R-10). |
| KI-07 | Rules | Unsaved rule and unit edits are discarded on Back without a prompt. Units are only saved together with the rules. |
| KI-08 | Library | The `All` and `Program` lists include disabled (not archived) exercises, with no "disabled" marker. |
| KI-09 | Rules | Today's warm-up and stretch counts ("8 moves") come from the cycle snapshot. Disabling a warm-up move shrinks the routine at once, but the count text only updates after the rules are applied. |
| KI-10 | Docs | The comment in `SeedData.DEFAULT_METRICS` says "7 enabled"; there are actually 9. |

---

## 11. Regression log: bugs fixed in this pass

Each fix has an automated regression test. If any of these IDs fails, a fixed bug has come back.

| Bug | Symptom before the fix | Guarded by |
|---|---|---|
| Stretch step unreachable | Finishing the last circuit jumped Today to the next day. `FINISH WITH STRETCHING` never appeared, and a stretch was recorded on the wrong day. Fixed on `main` by requiring the stretch to settle a day. This pass added `SKIP STRETCHING` and the UI and repository coverage | TOD-06…08, TOD-10, TOD-11, CYC-05, `StretchHoldTest`, `FullCycleTest`, `ProgramRulesTest` |
| Today ignored routine rules | Cards always said "8". Disabled warm-up and stretch still showed, and the CTA still sent you to a disabled warm-up. The finished text always said "24 days" | TOD-09, CYC-01 |
| Circuits uncompletable after archiving | Archiving a program exercise mid-cycle left 12 tickable exercises against a frozen total of 13 | CAT-08, CAT-09, RTN-07, `CircuitCompositionTest` |
| Hard-coded Program, Progress and summary copy | Always "4 weeks · 6 days a week · 132 circuits", "6 days", "4-week map", "Four weeks done". Program also ignored `Lock future days` and mislabelled previews after a shape change | PRG-07, PRG-08, CYC-02, `WeeksDoneHeadlineTest` |
| Editor bypassed the last-program-exercise guard | The editor could disable or re-slot the last program exercise | CAT-10 |
| Custom metric overwrote a built-in | A custom "Waist" replaced the built-in waist metric | MEA-11 |
| Units ignored in places | Performance best load and volume, and lean mass, were always "kg". Waist:height was never shown. Unitless labels read "Resting heart rate ()" | LOG-08, MEA-08, MEA-09, MEA-02 |
| Archived metrics vanished | The Edit metrics `ARCHIVED` section could never show anything | MEA-05 |
| Log sheet pre-fill raced typing | The fields filled in asynchronously after the sheet opened and could overwrite typed values. They now open pre-filled | LOG-01, LOG-03 |
| Decimal-comma locales | On comma-decimal locales, values pre-filled as "80,0", which doesn't parse, so edits and pre-filled loads were silently dropped | `UnitsTest` (locale case) |
| Anonymous checklist checkboxes | TalkBack read an unlabelled "checkbox". Each is now `Mark <name> done` | LST-02, MAN-07 |
| Editor save and archive tied to the composition | Save, archive and restore ran in the screen's coroutine scope; they now run in the ViewModel, so they survive recomposition and navigate on the main thread | CAT-01, CAT-03, CAT-05 |
