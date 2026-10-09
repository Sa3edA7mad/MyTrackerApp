package com.example.mytrackerapp.e2e

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Today with several active programs, and training one in guided mode. */
@RunWith(AndroidJUnit4::class)
class ProgramsFlowE2eTest : E2eTest() {

    override suspend fun arrange() = activateStarters()

    @Test
    fun pgm05_todayHasAChipPerActiveProgramAndEachKeepsItsOwnDay() {
        see("Home 4-Week")
        see("Gym Strength")
        see("CrossFit Conditioning")
        see("4 circuits · 13 exercises each")
        tapText("Gym Strength")
        see("1 circuit · 13 exercises")
        see("Squat + Bench")
        tapText("CrossFit Conditioning")
        see("1 circuit · 12 exercises")
        see("EMOM 12")
        tapText("Home 4-Week")
        see("4 circuits · 13 exercises each")
    }

    @Test
    fun pgm21_todayReopensOnTheProgramYouLeftItOn() {
        tapText("Gym Strength")
        see("Squat + Bench")
        relaunch()
        see("Squat + Bench")
        see("1 circuit · 13 exercises")
    }

    @Test
    fun pgm06_gymSetsRunStraightThroughALogSheetAndARest() {
        tapText("Gym Strength")
        tapText("Circuit 1")
        see("CIRCUIT 1 · SQUAT + BENCH · W1 D1")
        see("Barbell Back Squat")
        see("SET 1 OF 5")
        see("KEY CUE")
        tapButton("✓  Done")
        see("RPE") // a barbell lift tracks reps and load, so the log sheet opens first
        tapButton("Save")
        seeButton("Skip rest")
        seeDesc("120 second hold", substring = true)
        tapButton("Skip rest")
        see("SET 2 OF 5")
        see("2/13")
    }

    @Test
    fun pgm07_theEmomClockStartsAtMinuteOne() {
        tapText("CrossFit Conditioning")
        tapText("Circuit 1")
        see("MINUTE 1 / 12")
        tapButton("Start clock")
        seeButton("Reset clock")
    }

    @Test
    fun pgm08_anAmrapClockCountsRoundsAndSavesTheScore() {
        openTab("PROGRAMS")
        tapText("CrossFit Conditioning")
        tapDesc("Week 1 day 2, not started")
        tapText("Circuit 1")
        see("AMRAP 15 MIN")
        tapButton("Start clock")
        tapButton("+1 round")
        tapButton("+1 round")
        seeButton("Save 2 rounds")
        tapButton("Save 2 rounds")
        see("Saved: 2 rounds")
    }

    @Test
    fun pgm09_progressHasAProgramPicker() {
        openTab("PROGRESS")
        see("Week 1 · 4/day")
        tapText("Gym Strength")
        see("Week 1 · 1/day")
        tapText("Home 4-Week")
        see("Week 1 · 4/day")
    }

    @Test
    fun pgm10_aCircuitEditWaitsForApplyThenReachesToday() {
        openTab("PROGRAMS")
        tapText("Gym Strength")
        tapButton("Edit program")
        tapText("Squat + Bench")
        tapButton("+ Add exercise")
        typeInto("Search", "face pull")
        tapText("Face Pull")
        tapButton("Save")
        see("4 exercises · 14 sets", substring = true)

        // The running cycle still runs the old circuit until the change is applied.
        tapDesc("Back")
        openTab("TODAY")
        tapText("Gym Strength")
        see("1 circuit · 13 exercises")
    }

    @Test
    fun pgm11_applyingAPlanEditChangesTheRunningCycle() {
        openTab("PROGRAMS")
        tapText("Gym Strength")
        tapButton("Edit program")
        tapText("Squat + Bench")
        tapButton("+ Add exercise")
        typeInto("Search", "face pull")
        tapText("Face Pull")
        tapButton("Save")
        tapText("Apply changes to current cycle")
        tapInDialog("APPLY")
        see("Applied — the current cycle now runs this plan.")
        tapDesc("Back")
        openTab("TODAY")
        tapText("Gym Strength")
        see("1 circuit · 14 exercises")
    }

    @Test
    fun pgm12_aDayCanRunSeveralCircuits() {
        openTab("PROGRAMS")
        tapText("Gym Strength")
        tapButton("Edit program")
        // Chips repeat per day; the first match is the circuit row, the second is Day 1's chip.
        rule.onAllNodes(hasText("Deadlift + Pull"))[1].performScrollTo().performClick()
        rule.waitForIdle()
        tapText("Apply changes to current cycle")
        tapInDialog("APPLY")
        see("Applied — the current cycle now runs this plan.")
        tapDesc("Back")
        openTab("TODAY")
        tapText("Gym Strength")
        see("2 circuits · 27 exercises")
        see("Squat + Bench")
        see("Deadlift + Pull")
    }

    @Test
    fun pgm13_archivingHidesAProgramFromTodayAndRestoreBringsItBackPaused() {
        openTab("PROGRAMS")
        tapText("Gym Strength")
        tapButton("Edit program")
        tapText("Archive program")
        see("Restore program")
        tapDesc("Back")
        see("Gym Strength · archived")
        openTab("TODAY")
        dontSee("Gym Strength")
        see("CrossFit Conditioning")

        openTab("PROGRAMS")
        tapText("Gym Strength · archived")
        tapButton("Edit program")
        tapText("Restore program")
        tapDesc("Back")
        see("Gym Strength · paused")
    }

    @Test
    fun pgm14_aProgramsRulesAreItsOwn() {
        openTab("PROGRAMS")
        tapText("Gym Strength")
        tapButton("Edit program")
        tapText("Rules")
        see("Program rules")
        see("4 weeks · 3 days · 3 circuits")
        see("ROUNDS OF EACH DAY'S CIRCUITS")
    }

    @Test
    fun pgm15_homesSlotBackedCircuitCanBecomeAHandPickedList() {
        openTab("PROGRAMS")
        tapButton("Edit program")
        see("Every enabled program exercise in the Library")
        tapText("Circuit")
        see("Library's program slot", substring = true)
        tapButton("Choose exercises instead")
        see("Squat")
        see("Band Row")
        tapButton("Save")
        see("13 exercises · 13 sets")
        // The running cycle keeps its snapshot, so Today is unchanged.
        tapDesc("Back")
        openTab("TODAY")
        see("4 circuits · 13 exercises each")
    }
}

/** List mode: each set of a multi-set exercise is its own row. */
@RunWith(AndroidJUnit4::class)
class ProgramsListE2eTest : E2eTest() {

    override suspend fun arrange() {
        activateStarters()
        container.settings.setGuidedMode(false)
    }

    @Test
    fun pgm16_everySetIsItsOwnTickableRow() {
        tapText("Gym Strength")
        tapText("Circuit 1")
        see("GYM · 10")
        see("CORE · 3")
        see("Set 1 of 5", substring = true)
        see("Set 5 of 5", substring = true)
        tapDesc("Mark Barbell Back Squat set 1 of 5 done")
        tapButton("Skip") // the log sheet — a barbell lift tracks reps and load
        desc("Mark Barbell Back Squat set 1 of 5 done").assertIsOn()
        seeButton("✓ Complete all (12 left)")
        tapDesc("Mark Barbell Back Squat set 1 of 5 done")
        seeButton("✓ Complete all (13 left)")
    }
}

/** Ticks in one program never touch another's numbers, and reset only clears one cycle. */
@RunWith(AndroidJUnit4::class)
class ProgramsIsolationE2eTest : E2eTest() {

    override suspend fun arrange() {
        activateStarters()
        container.repo.setExerciseDone(1, 1, 1, "squat", true)
        container.repoFor(2).setExerciseDone(1, 1, 1, "barbell_back_squat#1", true)
        container.repoFor(2).setExerciseDone(1, 1, 1, "barbell_back_squat#2", true)
    }

    @Test
    fun pgm17_eachProgramShowsItsOwnProgress() {
        see("1/13")
        tapText("Gym Strength")
        see("2/13")
    }

    @Test
    fun pgm18_resettingAProgramOnlyClearsItsOwnCycle() {
        openTab("PROGRAMS")
        tapText("Gym Strength")
        tapButton("Edit program")
        tapText("Reset this cycle's progress")
        tapInDialog("RESET")
        see("This cycle's progress was reset.")
        tapDesc("Back")
        openTab("TODAY")
        tapText("Gym Strength")
        see("0/13")
        tapText("Home 4-Week")
        see("1/13")
    }
}

/** A finished program shows its own summary and can start a new cycle without touching others. */
@RunWith(AndroidJUnit4::class)
class ProgramsCompletedE2eTest : E2eTest() {

    override suspend fun arrange() {
        activateStarters()
        val gym = container.repoFor(2)
        for (week in 1..4) for (day in 1..3) gym.closeDayEarly(week, day)
    }

    @Test
    fun pgm20_aFinishedProgramHasItsOwnSummaryAndRestart() {
        tapText("Gym Strength")
        see("CYCLE COMPLETE")
        tapButton("See cycle summary")
        seeButton("Start a new cycle")
        tapButton("Start a new cycle")
        see("Home 4-Week")
        tapText("Gym Strength")
        see("Squat + Bench")
        dontSee("CYCLE COMPLETE")
        tapText("Home 4-Week")
        see("4 circuits · 13 exercises each")
    }
}

/** A load override is typed in the display unit and stored in kilograms. */
@RunWith(AndroidJUnit4::class)
class ProgramsPoundsE2eTest : E2eTest() {

    override suspend fun arrange() {
        container.rules.saveDraft(container.rules.getDraft(), com.example.mytrackerapp.domain.UnitPrefs(
            weight = com.example.mytrackerapp.domain.WeightUnit.LB
        ))
    }

    @Test
    fun pgm22_aLoadOverrideIsEnteredInPoundsAndStoredInKilograms() {
        openTab("PROGRAMS")
        tapText("Gym Strength · paused")
        tapButton("Edit program")
        tapText("Squat + Bench")
        typeInto("Load (lb)", "220")
        tapButton("Save")
        tapText("Squat + Bench")
        seeFieldValue("Load (lb)", "220")
        runBlocking {
            val plan = container.rulesFor(2).getPlan()
            val kg = plan.circuits.first().items.first().loadKg!!
            check(kotlin.math.abs(kg - 99.79) < 0.01) { "stored $kg kg" }
        }
    }
}
