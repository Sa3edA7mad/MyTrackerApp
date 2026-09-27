package com.example.mytrackerapp.e2e

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** LIB-xx in docs/FEATURES.md: Library browse, search, filter and exercise detail. */
@RunWith(AndroidJUnit4::class)
class LibraryE2eTest : E2eTest() {

    private fun search(query: String) {
        node(hasSetTextAction()).performTextReplacement(query)
        rule.waitForIdle()
    }

    @Test
    fun lib01_listsTheWholeCatalogByCategory() {
        openTab("LIBRARY")
        see("29 moves")
        see("BODYWEIGHT · 6")
        see("Squat")
        see("Legs · Glutes · Core")
        see("RESISTANCE BAND · 7")
        see("WARM-UP · 8")
        see("STRETCH · 8")
        see("Spinal Twist")
    }

    @Test
    fun lib02_filterChipsNarrowTheList() {
        openTab("LIBRARY")
        tapText("Program")
        see("13 moves")
        tapText("Warm-up")
        see("8 moves")
        see("Neck Rolls")
        tapText("Stretch")
        see("8 moves")
        tapText("Archived")
        see("0 moves")
        see("Nothing here yet.")
        tapText("All")
        see("29 moves")
    }

    @Test
    fun lib03_searchMatchesNamesAndMuscles() {
        openTab("LIBRARY")
        search("glute")
        see("Glute Bridge")
        see("Squat") // "Glutes" in its muscles
        search("zzz")
        see("No exercise matches “zzz”.")
    }

    @Test
    fun lib04_exerciseDetailShowsHowToVideoAndEmptyHistory() {
        openTab("LIBRARY")
        tapText("Squat")
        see("BODYWEIGHT")
        see("Squat")
        see("REPS")
        see("HOW TO")
        see("Watch form video")
        see("YOUR HISTORY")
        see("Not done yet this cycle.")
        tapDesc("Back")
        see("29 moves")
    }

    @Test
    fun lib05_perSideExercisesSayEachSide() {
        openTab("LIBRARY")
        tapText("External Rotation")
        see("EACH SIDE")
    }
}

@RunWith(AndroidJUnit4::class)
class LibraryHistoryE2eTest : E2eTest() {

    override suspend fun arrange() {
        completeCircuit(1, 1, 1)
        completeCircuit(1, 1, 2)
    }

    @Test
    fun lib06_detailCountsThisCyclesCompletions() {
        openTab("LIBRARY")
        tapText("Squat")
        see("2")
        see(" completions this cycle")
        see("LAST 1 TRAINING DAY")
        see("PERFORMANCE")
        see("No sets logged yet. Turn on rep or load logging in the exercise editor.")
    }
}

/** CAT-xx in docs/FEATURES.md: the exercise editor. */
@RunWith(AndroidJUnit4::class)
class CatalogEditorE2eTest : E2eTest() {

    @Test
    fun cat01_addingAnExerciseNeedsANameAndShowsInTheLibrary() {
        openTab("LIBRARY")
        tapDesc("Add exercise")
        see("New exercise")
        see("Name can't be empty.")
        button("Save").assertIsNotEnabled()
        typeInto("Name", "Pistol Squat")
        dontSee("Name can't be empty.")
        tapButton("Save")
        see("30 moves")
        tapText("Program")
        see("14 moves")
        see("Pistol Squat")
    }

    @Test
    fun cat02_anInvalidVideoUrlBlocksSaving() {
        openTab("LIBRARY")
        tapDesc("Add exercise")
        typeInto("Name", "Test Move")
        typeInto("Video URL", "youtube.com/watch")
        see("Video URL must start with http:// or https://, or be left blank.")
        button("Save").assertIsNotEnabled()
    }

    @Test
    fun cat03_editingRenamesWithoutLosingTheExercise() {
        openTab("LIBRARY")
        tapText("Squat")
        tapDesc("Edit exercise")
        see("Edit exercise")
        typeInto("Name", "Air Squat")
        tapButton("Save")
        see("Air Squat")
        tapDesc("Back")
        see("Air Squat")
        see("29 moves")
    }

    @Test
    fun cat04_archiveAsksThenMovesItToArchived() {
        openTab("LIBRARY")
        tapText("Squat")
        tapDesc("Edit exercise")
        tapText("Archive")
        see("Archive this exercise?")
        tapText("CANCEL")
        tapText("Archive")
        tapText("ARCHIVE")
        see("ARCHIVED — kept for history, no longer in the rotation")
        tapDesc("Back")
        see("28 moves")
        tapText("Archived")
        see("1 move")
        see("Squat")
    }

    @Test
    fun cat05_restoreBringsItBack() {
        openTab("LIBRARY")
        tapText("Squat")
        tapDesc("Edit exercise")
        tapText("Archive")
        tapText("ARCHIVE")
        tapDesc("Edit exercise")
        see("ARCHIVED")
        tapText("Restore")
        dontSee("ARCHIVED — kept for history, no longer in the rotation")
        tapDesc("Back")
        see("29 moves")
    }

    @Test
    fun cat06_loadTrackingRevealsDefaultLoadAndBand() {
        openTab("LIBRARY")
        tapText("Band Row")
        tapDesc("Edit exercise")
        dontSee("Default load (kg)")
        tapText("Log load")
        field("Default load (kg)")
        field("Default band")
    }

    @Test
    fun cat07_programExerciseEditsWarnAboutApplyingRules() {
        openTab("LIBRARY")
        tapDesc("Add exercise")
        see("Changing a program exercise changes the draft's exercises-per-circuit. " +
            "Applies to the current cycle only after you apply rules.")
        tapText("WARMUP")
        dontSee("Changing a program exercise", substring = true)
    }
}
