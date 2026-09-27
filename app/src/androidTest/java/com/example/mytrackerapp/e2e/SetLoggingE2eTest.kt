package com.example.mytrackerapp.e2e

import androidx.compose.ui.test.assertIsOn
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.WeightUnit
import org.junit.Test
import org.junit.runner.RunWith

/** LOG-xx in docs/FEATURES.md: rep/load logging. */
@RunWith(AndroidJUnit4::class)
class SetLoggingE2eTest : E2eTest() {

    override suspend fun arrange() {
        container.settings.setGuidedMode(false)
        container.repo.markWarmUpDone(1, 1)
        editExercise("squat") { it.copy(tracksReps = true, tracksLoad = true, defaultLoadKg = 10.0) }
        editExercise("push_up") { it.copy(tracksReps = true) }
    }

    @Test
    fun log01_tickingATrackedExerciseOpensAPrefilledSheet() {
        tapButton("Start circuit 1")
        tapDesc("Mark Squat done")
        see("RPE")
        seeFieldValue("Reps", "5")
        seeFieldValue("Load (kg)", "10")
        seeButton("Save")
        seeButton("Skip")
    }

    @Test
    fun log02_savedDetailFeedsTheExercisePerformance() {
        tapButton("Start circuit 1")
        tapDesc("Mark Squat done")
        seeFieldValue("Reps", "5")
        typeInto("Reps", "7")
        tapText("8")
        tapButton("Save")
        desc("Mark Squat done").assertIsOn()
        tapDesc("Close circuit")

        openTab("LIBRARY")
        tapText("Squat")
        see("PERFORMANCE")
        see("10.0 kg")
        see("7")
        see("70 kg")
        see("VOLUME BY TRAINING DAY")
    }

    @Test
    fun log03_theNextSheetPrefillsFromTheLastLoggedSet() {
        tapButton("Start circuit 1")
        tapDesc("Mark Squat done")
        seeFieldValue("Reps", "5")
        typeInto("Reps", "9")
        typeInto("Load (kg)", "12.5")
        tapButton("Save")
        desc("Mark Squat done").assertIsOn()
        tapDesc("Close circuit")
        tapText("Circuit 2")
        tapDesc("Mark Squat done")
        seeFieldValue("Reps", "9")
        seeFieldValue("Load (kg)", "12.5")
    }

    @Test
    fun log04_skippingTheSheetStillTicks() {
        tapButton("Start circuit 1")
        tapDesc("Mark Push-up done")
        see("Reps")
        tapButton("Skip")
        desc("Mark Push-up done").assertIsOn()
        seeButton("✓ Complete all (12 left)")
    }

    @Test
    fun log05_anUntrackedExerciseTicksWithoutASheet() {
        tapButton("Start circuit 1")
        tapDesc("Mark Crunch done")
        desc("Mark Crunch done").assertIsOn()
        dontSee("RPE")
    }

    @Test
    fun log06_guidedDoneOpensTheSheetBeforeAdvancing() {
        tapButton("Start circuit 1")
        tapText("⇄ GUIDED")
        see("Squat")
        tapButton("✓  Done")
        see("RPE")
        tapButton("Save")
        see("Push-up")
        see("2/13")
    }

    @Test
    fun log07_anExerciseWithoutLoggedSetsExplainsHowToTurnItOn() {
        tapButton("Start circuit 1")
        tapDesc("Mark Crunch done")
        tapDesc("Close circuit")
        openTab("LIBRARY")
        tapText("Crunch")
        see("No sets logged yet. Turn on rep or load logging in the exercise editor.")
    }
}

/** LOG-08: logged load follows the display unit (regression: it was always "kg"). */
@RunWith(AndroidJUnit4::class)
class SetLoggingPoundsE2eTest : E2eTest() {

    override suspend fun arrange() {
        container.settings.setGuidedMode(false)
        applyRules(units = UnitPrefs(weight = WeightUnit.LB))
        editExercise("squat") { it.copy(tracksReps = true, tracksLoad = true, defaultLoadKg = 10.0) }
    }

    @Test
    fun log08_loadIsEnteredAndShownInPounds() {
        tapText("Circuit 1")
        tapDesc("Mark Squat done")
        seeFieldValue("Load (lb)", "22.0")
        typeInto("Reps", "5")
        typeInto("Load (lb)", "22")
        tapButton("Save")
        tapDesc("Close circuit")
        openTab("LIBRARY")
        tapText("Squat")
        see("22.0 lb")
        see("110 lb")
    }
}
