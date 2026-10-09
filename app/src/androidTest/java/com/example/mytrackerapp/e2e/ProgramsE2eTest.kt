package com.example.mytrackerapp.e2e

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Programs: picking, editing, activating, and training a second program from Today. */
@RunWith(AndroidJUnit4::class)
class ProgramsE2eTest : E2eTest() {

    @Test
    fun pgm01_theProgramsTabListsEveryProgramAndShowsTheSelectedGrid() {
        openTab("PROGRAMS")
        see("Home 4-Week")
        tapText("Gym Strength · paused")
        see("4 weeks · 3 days a week · 12 circuits")
        tapDesc("Week 1 day 1, not started")
        see("Squat + Bench")
    }

    @Test
    fun pgm02_activatingAProgramPutsItOnTodayBesideHome() {
        openTab("PROGRAMS")
        tapText("Gym Strength · paused")
        tapButton("Edit program")
        see("Gym Strength")
        tapText("Active")
        see("Shown on Today")
        tapDesc("Back")
        openTab("TODAY")
        see("Home 4-Week")
        tapText("Gym Strength")
        see("Squat + Bench")
        tapText("Circuit 1")
        see("Barbell Back Squat")
        see("SET 1 OF 5")
        see("KEY CUE")
    }

    @Test
    fun pgm05b_progressCanShowAPausedProgram() {
        openTab("PROGRESS")
        see("Week 1 · 4/day")
        tapText("Gym Strength · paused")
        see("Week 1 · 1/day")
    }

    @Test
    fun pgm03_aNewProgramStartsPausedAndSaysWhatItNeeds() {
        openTab("PROGRAMS")
        tapButton("+ New program")
        node(hasSetTextAction()).performTextReplacement("Travel")
        tapText("SAVE")
        see("Travel")
        see("• Circuit A has no exercises.")
        tapText("Active")
        see("Can't turn this program on yet", substring = true)
    }

    @Test
    fun pgm04_aCircuitCanBeBuiltFromTheLibrary() {
        openTab("PROGRAMS")
        tapButton("+ New program")
        node(hasSetTextAction()).performTextReplacement("Travel")
        tapText("SAVE")
        tapText("Circuit A")
        tapButton("+ Add exercise")
        node(hasSetTextAction() and androidx.compose.ui.test.hasText("Search", substring = true))
            .performTextReplacement("burpee")
        tapText("Burpee")
        see("Burpee")
        tapDesc("Increase Sets")
        tapButton("Save")
        see("1 exercise · 2 sets")
        tapText("Active")
        see("Shown on Today")
    }
}
