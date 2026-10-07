package com.example.mytrackerapp.e2e

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** RUL-xx in docs/FEATURES.md: the program rules editor. */
@RunWith(AndroidJUnit4::class)
class RulesE2eTest : E2eTest() {

    private fun openRules() {
        tapDesc("Settings")
        tapText("Program rules")
        see("4 weeks · 6 days · 13 exercises a circuit")
    }

    @Test
    fun rul01_showsTheShippedRulesAndALosslessImpact() {
        openRules()
        see("SHAPE")
        see("CIRCUITS A DAY")
        see("8 moves before your first circuit")
        see("8 stretches after your last circuit")
        see("1716 → 1716 exercises · 24 days")
        seeButton("Apply to current cycle")
        seeButton("Save for next cycle")
    }

    @Test
    fun rul02_saveForNextCycleLeavesTheRunningCycleAlone() {
        openRules()
        tapDesc("Increase Week 1")
        see("1716 → 1794 exercises · 24 days")
        tapButton("Save for next cycle")
        see("Saved for the next cycle.")
        tapDesc("Back")
        tapDesc("Back")
        see("4 circuits · 13 exercises each")
    }

    @Test
    fun rul03_applyAsksThenChangesTheRunningCycle() {
        openRules()
        tapDesc("Increase Week 1")
        tapButton("Apply to current cycle")
        see("Apply to the current cycle?")
        see("This change is lossless.", substring = true)
        tapText("CANCEL")
        tapButton("Apply to current cycle")
        tapText("APPLY")
        see("Applied to the current cycle.")
        tapDesc("Back")
        tapDesc("Back")
        see("5 circuits · 13 exercises each")
    }

    @Test
    fun rul04_addingAWeekAddsItsCircuitStepper() {
        openRules()
        tapDesc("Increase Weeks")
        see("5 weeks · 6 days · 13 exercises a circuit")
        see("Week 5")
        see("1716 → 2262 exercises · 30 days")
    }

    @Test
    fun rul05_invalidRulesShowWhyAndCannotBeSaved() {
        openRules()
        tapText("Count warm-up and stretch in totals")
        tapText("Warm-up enabled")
        tapText("Stretch enabled")
        see("Warm-up and stretch are both off, so there is nothing for " +
            "\"count them in totals\" to count.")
        button("Save for next cycle").assertIsNotEnabled()
        button("Apply to current cycle").assertIsNotEnabled()
    }

    @Test
    fun rul06_restoreDefaultsAsksThenResetsTheDraft() {
        openRules()
        tapDesc("Increase Weeks")
        tapButton("Save for next cycle")
        tapText("Restore default rules")
        see("Restore default rules?")
        tapText("RESTORE")
        see("4 weeks · 6 days · 13 exercises a circuit")
    }

    @Test
    fun rul07_rulesAreKeptAfterLeaving() {
        openRules()
        tapDesc("Increase Days per week")
        tapButton("Save for next cycle")
        see("Saved for the next cycle.")
        tapDesc("Back")
        tapText("Program rules")
        see("4 weeks · 7 days · 13 exercises a circuit")
    }

    @Test
    fun rul08_unitTogglesSaveWithTheRules() {
        openRules()
        tapText("LB")
        tapText("IN")
        tapButton("Save for next cycle")
        see("Saved for the next cycle.")
        tapDesc("Back")
        tapText("Body measurements")
        tapDesc("Log measurements")
        field("Body weight (lb)")
        field("Waist (in)")
    }
}

/** RUL-09: applying a shrinking change warns about completions that stop counting. */
@RunWith(AndroidJUnit4::class)
class RulesLossyApplyE2eTest : E2eTest() {

    override suspend fun arrange() {
        completeCircuit(1, 1, 4)
    }

    @Test
    fun rul09_shrinkingWarnsBeforeApplying() {
        tapDesc("Settings")
        tapText("Program rules")
        tapDesc("Decrease Week 1")
        see("13 completion(s) would stop counting (nothing is deleted).", substring = true)
        tapButton("Apply to current cycle")
        see("13 completion(s) would stop counting.", substring = true)
        tapText("APPLY")
        tapDesc("Back")
        tapDesc("Back")
        see("3 circuits · 13 exercises each")
        dontSee("Circuit 4")
    }
}

/** SET-xx in docs/FEATURES.md: Settings. */
@RunWith(AndroidJUnit4::class)
class SettingsE2eTest : E2eTest() {

    @Test
    fun set01_listsEverySetting() {
        tapDesc("Settings")
        see("Program rules")
        see("Body measurements")
        see("Guided mode by default")
        see("Timer auto-advance")
        see("Keep screen awake")
        see("Sound cues")
        see("Haptics")
        see("Export training data")
        see("Reset current cycle")
        see("Match device")
        see("Light charcoal")
        see("Steel glass")
        see("Pale frost")
    }

    @Test
    fun set02_togglesSurviveARelaunch() {
        tapDesc("Settings")
        text("Haptics").assertIsOn()
        tapText("Haptics")
        text("Haptics").assertIsOff()
        tapText("Timer auto-advance")
        relaunch()
        tapDesc("Settings")
        text("Haptics").assertIsOff()
        text("Timer auto-advance").assertIsOff()
    }

    @Test
    fun set05_themeChoiceSurvivesARelaunch() {
        tapDesc("Settings")
        text("Match device", substring = true).assertIsSelected()
        tapText("Steel glass")
        text("Steel glass", substring = true).assertIsSelected()
        text("Match device", substring = true).assertIsNotSelected()
        relaunch()
        tapDesc("Settings")
        text("Steel glass", substring = true).assertIsSelected()
    }

    @Test
    fun set03_guidedModeOffOpensCircuitsAsAList() {
        tapDesc("Settings")
        tapText("Guided mode by default")
        tapDesc("Back")
        tapText("Circuit 1")
        seeButton("✓ Complete all (13 left)")
    }
}

@RunWith(AndroidJUnit4::class)
class SettingsResetE2eTest : E2eTest() {

    override suspend fun arrange() {
        completeCircuit(1, 1, 1)
    }

    @Test
    fun set04_resetCycleAsksThenClearsProgress() {
        seeDesc("1 of 4 circuits complete")
        tapDesc("Settings")
        tapText("Reset current cycle")
        see("Reset this cycle?")
        tapText("CANCEL")
        tapText("Reset current cycle")
        tapText("RESET CYCLE")
        tapDesc("Back")
        seeDesc("0 of 4 circuits complete")
        see("WEEK 1 · DAY 1")
    }
}
