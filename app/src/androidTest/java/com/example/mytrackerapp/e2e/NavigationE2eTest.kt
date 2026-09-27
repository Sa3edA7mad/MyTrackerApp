package com.example.mytrackerapp.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** NAV-xx in docs/FEATURES.md. */
@RunWith(AndroidJUnit4::class)
class NavigationE2eTest : E2eTest() {

    @Test
    fun nav01_bottomBarSwitchesBetweenTheFourTabs() {
        see("Today")
        openTab("PROGRAM")
        see("Program")
        see("4 weeks · 6 days a week · 132 circuits")
        openTab("PROGRESS")
        see("Progress")
        see("Current cycle")
        openTab("LIBRARY")
        see("Library")
        see("29 moves")
        openTab("TODAY")
        see("WEEK 1 · DAY 1")
    }

    @Test
    fun nav02_settingsIsImmersiveAndBackReturnsToToday() {
        tapDesc("Settings")
        see("Settings")
        dontSee("LIBRARY") // bottom bar hidden
        tapDesc("Back")
        see("WEEK 1 · DAY 1")
        see("LIBRARY")
    }

    @Test
    fun nav03_systemBackLeavesACircuit() {
        tapText("Circuit 1")
        see("CIRCUIT 1 · WEEK 1 DAY 1")
        dontSee("TODAY")
        back()
        see("WEEK 1 · DAY 1")
    }

    @Test
    fun nav04_tabStateIsKeptWhenSwitchingTabs() {
        openTab("LIBRARY")
        tapText("Stretch")
        see("8 moves")
        openTab("TODAY")
        openTab("LIBRARY")
        see("8 moves")
    }
}
