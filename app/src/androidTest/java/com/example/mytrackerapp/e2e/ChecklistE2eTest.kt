package com.example.mytrackerapp.e2e

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** LST-xx in docs/FEATURES.md: list (checklist) mode. */
@RunWith(AndroidJUnit4::class)
class ChecklistE2eTest : E2eTest() {

    override suspend fun arrange() {
        container.settings.setGuidedMode(false)
        container.repo.markWarmUpDone(1, 1)
    }

    private fun openCircuitOne() {
        tapButton("Start circuit 1")
        see("CIRCUIT 1")
    }

    @Test
    fun lst01_listsTheCircuitGroupedByCategory() {
        openCircuitOne()
        see("/13")
        see("BODYWEIGHT · 6")
        see("RESISTANCE BAND · 7")
        see("Squat")
        see("5 reps")
        seeDesc("Watch Squat video")
        see("Band Row")
        seeButton("✓ Complete all (13 left)")
        see("⇄ GUIDED")
    }

    @Test
    fun lst02_tickAndUntick() {
        openCircuitOne()
        tapDesc("Mark Squat done")
        desc("Mark Squat done").assertIsOn()
        seeButton("✓ Complete all (12 left)")
        tapDesc("Mark Squat done")
        desc("Mark Squat done").assertIsOff()
        seeButton("✓ Complete all (13 left)")
    }

    @Test
    fun lst03_completeAllAsksFirst() {
        openCircuitOne()
        tapButton("✓ Complete all (13 left)")
        see("Complete all?")
        see("Mark the 13 remaining exercises done. Rep/load logging is skipped for these.")
        tapText("CANCEL")
        seeButton("✓ Complete all (13 left)")

        tapButton("✓ Complete all (13 left)")
        tapText("COMPLETE ALL")
        waitGone(androidx.compose.ui.test.hasText("✓ Complete all", substring = true))
        tapDesc("Close circuit")
        seeDesc("1 of 4 circuits complete")
        see("13/13")
    }

    @Test
    fun lst04_tappingANameRunsThatExerciseAlone() {
        openCircuitOne()
        tapText("External Rotation")
        see("SIDE 1")
        seeButton("Back to list")
        tapButton("✓  Done")
        see("SIDE 1 DONE")
        see("SWITCH SIDES")
        tapButton("Continue · side 2")
        see("SIDE 2")
        tapButton("✓  Done")
        seeButton("✓ Complete all (12 left)")
        desc("Mark External Rotation done").assertIsOn()
    }

    @Test
    fun lst05_backFromASingleExerciseReturnsToTheList() {
        openCircuitOne()
        tapText("Push-up")
        seeButton("Back to list")
        tapButton("Back to list")
        seeButton("✓ Complete all (13 left)")
        tapText("Crunch")
        back()
        seeButton("✓ Complete all (13 left)")
    }

    @Test
    fun lst06_switchingToGuidedBecomesTheDefault() {
        openCircuitOne()
        tapText("⇄ GUIDED")
        see("CIRCUIT 1 · WEEK 1 DAY 1")
        seeButton("⇄ Checklist")
    }

    @Test
    fun lst07_warmUpListMarksTheDayFlagWhenEverythingIsTicked() {
        tapDesc("End day early")
        tapText("END DAY") // day 2: warm-up not done yet
        tapText("WARM-UP")
        see("WARM-UP")
        seeButton("✓ Complete all (8 left)")
        seeButton("Done with warm-up")
        tapButton("✓ Complete all (8 left)")
        tapText("COMPLETE ALL")
        waitGone(androidx.compose.ui.test.hasText("✓ Complete all", substring = true))
        tapDesc("Close circuit")
        see("8 moves · done")
    }

    @Test
    fun lst08_doneWithWarmUpSetsTheFlagEarly() {
        tapDesc("End day early")
        tapText("END DAY")
        tapButton("Warm up, then circuit 1")
        tapButton("Done with warm-up")
        see("8 moves · done")
        seeButton("Start circuit 1")
    }
}

/** LST-09: list view is the escape hatch for locked future days. */
@RunWith(AndroidJUnit4::class)
class ChecklistFutureDayE2eTest : E2eTest() {

    override suspend fun arrange() {
        container.settings.setGuidedMode(false)
    }

    @Test
    fun lst09_aLockedFutureDayCanBeTickedFromTheList() {
        openTab("PROGRAM")
        tapDesc("Week 1 day 3, not started")
        tapText("Circuit 1")
        see("Future day — ticks here count for week 1 day 3.")
        tapDesc("Mark Squat done")
        seeButton("✓ Complete all (12 left)")
        tapDesc("Close circuit")
        seeDesc("Week 1 day 3, 1 of 52")
    }
}
