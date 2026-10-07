package com.example.mytrackerapp.e2e

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** GUI-xx in docs/FEATURES.md: guided mode (the default). */
@RunWith(AndroidJUnit4::class)
class GuidedCircuitE2eTest : E2eTest() {

    override suspend fun arrange() {
        container.repo.markWarmUpDone(1, 1)
    }

    private fun openCircuitOne() {
        tapButton("Start circuit 1")
        see("CIRCUIT 1 · WEEK 1 DAY 1")
    }

    @Test
    fun gui01_opensOnTheFirstExerciseWithItsDetails() {
        openCircuitOne()
        see("1/13")
        see("Squat")
        see("BODYWEIGHT")
        see("REPS")
        see("HOW TO")
        see("WATCH FORM VIDEO")
        seeButton("✓  Done")
        seeButton("Skip")
        seeButton("⇄ Checklist")
    }

    @Test
    fun gui02_doneRecordsAndAdvances() {
        openCircuitOne()
        tapButton("✓  Done")
        see("2/13")
        see("Push-up")
        tapDesc("Close circuit")
        see("1/13")
        see("51")
    }

    @Test
    fun gui03_skipAdvancesWithoutRecording() {
        openCircuitOne()
        tapButton("Skip")
        see("2/13")
        tapDesc("Close circuit")
        see("0/13")
    }

    @Test
    fun gui10_previousAndNextStepThroughExercisesIncludingDoneOnes() {
        openCircuitOne()
        button("‹ Previous").assertIsNotEnabled()
        tapButton("✓  Done")
        see("2/13")
        tapButton("‹ Previous")
        see("1/13")
        see("Squat")
        tapButton("Next ›")
        see("2/13")
        see("Push-up")
        tapDesc("Close circuit")
        see("1/13")
    }

    @Test
    fun gui11_steppingAwayFromAHoldResetsItsTimer() {
        openCircuitOne()
        tapButton("Next ›")
        tapButton("Next ›")
        see("Dead Hang")
        tapButton("Start · 15 sec")
        seeButton("❚❚ Pause")
        tapButton("Next ›")
        see("Crunch")
        tapButton("‹ Previous")
        see("Dead Hang")
        seeButton("Start · 15 sec")
    }

    @Test
    fun gui12_steppingAwayFromASideRestartsItAtSideOne() {
        openCircuitOne()
        repeat(12) { if (runCatching { waitFor(hasText("External Rotation"), 500) }.isFailure) tapButton("Next ›") }
        see("External Rotation")
        see("SIDE 1")
        tapButton("Done · side 1")
        see("SIDE 1 DONE")
        tapButton("‹ Previous")
        tapButton("Next ›")
        see("External Rotation")
        see("SIDE 1")
        seeButton("Done · side 1")
    }

    @Test
    fun gui13_nextWalksToTheEndWithoutRecordingAnything() {
        openCircuitOne()
        repeat(12) { tapButton("Next ›") }
        see("13/13")
        button("Next ›").assertIsNotEnabled()
        tapButton("‹ Previous")
        see("12/13")
        tapDesc("Close circuit")
        see("0/13")
    }

    @Test
    fun gui04_timedExerciseHasAStartPauseResetTimer() {
        openCircuitOne()
        tapButton("Skip")
        tapButton("Skip")
        see("Dead Hang")
        seeButton("Start · 15 sec")
        seeDesc("15 second hold, not started")
        tapButton("Start · 15 sec")
        seeButton("❚❚ Pause")
        tapButton("❚❚ Pause")
        seeButton("Resume")
        tapButton("Reset")
        seeButton("Start · 15 sec")
    }

    @Test
    fun gui05_skippingToTheEndOffersBackToMissed() {
        openCircuitOne()
        repeat(13) { tapButton("Skip") }
        see("0 of 13 done")
        see("You skipped 13 exercises", substring = true)
        tapText("BACK TO MISSED")
        see("1/13")
        see("Squat")
        repeat(13) { tapButton("Skip") }
        tapText("FINISH ANYWAY")
        see("WEEK 1 · DAY 1")
    }

    @Test
    fun gui06_switchingToChecklistBecomesTheDefault() {
        openCircuitOne()
        tapButton("⇄ Checklist")
        seeButton("✓ Complete all (13 left)")
        tapDesc("Close circuit")
        tapText("Circuit 2")
        see("CIRCUIT 2")
        seeButton("✓ Complete all (13 left)")
        tapDesc("Close circuit")
        tapDesc("Settings")
        text("Guided mode by default").assertIsOff()
    }
}

/** GUI-07..08: guided flows that need arranged state. */
@RunWith(AndroidJUnit4::class)
class GuidedCircuitArrangedE2eTest : E2eTest() {

    override suspend fun arrange() {
        container.repo.markWarmUpDone(1, 1)
        // Everything but the last exercise, so the pager opens on Band Row.
        completeCircuit(1, 1, 1, programIds.dropLast(1))
        // A 2-second hold so the auto-advance path runs in test time.
        editExercise("dead_hang") { it.copy(targetValue = 2) }
    }

    @Test
    fun gui07_finishingTheLastExerciseClosesTheCircuit() {
        tapButton("Start circuit 1")
        see("13/13")
        see("Band Row")
        tapButton("✓  Done")
        seeDesc("1 of 4 circuits complete")
        see("13/13")
    }

    @Test
    fun gui08_aFinishedHoldAutoAdvancesAndRecords() {
        tapText("Circuit 2")
        tapButton("Skip")
        tapButton("Skip")
        see("Dead Hang")
        tapButton("Start · 2 sec")
        // 2 s hold + 0.7 s pause, then the pager moves on by itself.
        waitFor(hasText("Crunch"), timeoutMs = 8_000)
        see("4/13")
        tapDesc("Close circuit")
        see("1/13")
    }
}

/** GUI-09: a locked future day opens read-only in guided mode. */
@RunWith(AndroidJUnit4::class)
class LockedDayGuidedE2eTest : E2eTest() {

    @Test
    fun gui09_futureDayIsAReadOnlyPreview() {
        openTab("PROGRAM")
        tapDesc("Week 1 day 2, not started")
        see("Day 2 · preview — finish the current day first")
        tapText("Circuit 1")
        see("CIRCUIT 1 · WEEK 1 DAY 2")
        see("Preview — finish the current day before training this one.")
        tapButton("Next ›")
        see("2/13")
        tapButton("‹ Previous")
        see("1/13")
        button("✓  Done").assertIsNotEnabled()
    }
}
