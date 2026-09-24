package com.example.mytrackerapp.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** PRG-xx in docs/FEATURES.md: the Program tab. */
@RunWith(AndroidJUnit4::class)
class ProgramE2eTest : E2eTest() {

    override suspend fun arrange() {
        completeCircuit(1, 1, 1)
    }

    @Test
    fun prg01_showsEveryWeekWithItsShape() {
        openTab("PROGRAM")
        see("4 weeks · 6 days a week · 132 circuits")
        see("WEEK 1")
        see("4 circuits/day · 6 days")
        see("1/24")
        see("WEEK 4")
        see("7 circuits/day · 6 days")
        see("0/42")
    }

    @Test
    fun prg02_daySquaresDescribeTheirState() {
        openTab("PROGRAM")
        seeDesc("Week 1 day 1, 13 of 52")
        seeDesc("Week 1 day 2, not started")
        seeDesc("Week 4 day 6, not started")
    }

    @Test
    fun prg03_tappingTheCurrentDayExpandsItsCircuits() {
        openTab("PROGRAM")
        tapDesc("Week 1 day 1, 13 of 52")
        see("Day 1 · 13/52 exercises")
        see("13/13")
        tapText("Circuit 2")
        see("CIRCUIT 2 · WEEK 1 DAY 1")
    }

    @Test
    fun prg04_tappingTheSameDayAgainCollapsesIt() {
        openTab("PROGRAM")
        tapDesc("Week 1 day 1, 13 of 52")
        see("Day 1 · 13/52 exercises")
        tapDesc("Week 1 day 1, 13 of 52")
        dontSee("Day 1 · 13/52 exercises")
    }

    @Test
    fun prg05_aFutureDayIsLabelledAPreview() {
        openTab("PROGRAM")
        tapDesc("Week 2 day 1, not started")
        see("Day 1 · preview — finish the current day first")
    }

    @Test
    fun prg06_anEndedDayReadsEndedEarly() {
        tapDesc("End day early")
        tapText("END DAY")
        openTab("PROGRAM")
        seeDesc("Week 1 day 1, ended early, 13 of 52")
    }
}

/** PRG-07..08: Program follows edited rules (regression for the hard-coded header/lock). */
@RunWith(AndroidJUnit4::class)
class ProgramCustomRulesE2eTest : E2eTest() {

    override suspend fun arrange() {
        applyRules {
            it.copy(weeks = 2, daysPerWeek = 3, circuitsPerWeek = listOf(2, 3), lockFutureDays = false)
        }
    }

    @Test
    fun prg07_headerAndWeeksFollowTheRules() {
        openTab("PROGRAM")
        see("2 weeks · 3 days a week · 15 circuits")
        see("2 circuits/day · 3 days")
        see("3 circuits/day · 3 days")
        dontSee("WEEK 3")
    }

    @Test
    fun prg08_withLockingOffAFutureDayIsNotAPreview() {
        openTab("PROGRAM")
        tapDesc("Week 2 day 3, not started")
        see("Day 3 · 0/39 exercises")
        dontSee("preview", substring = true)
    }
}

/** PRS-xx in docs/FEATURES.md: the Progress tab. */
@RunWith(AndroidJUnit4::class)
class ProgressEmptyE2eTest : E2eTest() {

    @Test
    fun prs01_aFreshCycleReadsZero() {
        openTab("PROGRESS")
        see("STREAK")
        see("0%")
        see("EX. DONE")
        see("4-WEEK MAP")
        seeDesc("Week 1 day 1, not started")
        see("Complete")
        see("Partial")
        see("Not yet")
        see("Week 1 · 4/day")
        see("0/24")
        see("Complete your first circuit to start a streak.")
    }

    @Test
    fun prs02_bodyMeasurementsRowOpensMeasurements() {
        openTab("PROGRESS")
        tapText("Body measurements")
        see("DERIVED")
        see("BMI")
    }
}

@RunWith(AndroidJUnit4::class)
class ProgressWithWorkE2eTest : E2eTest() {

    override suspend fun arrange() {
        completeDay(1, 1)
        container.repo.markStretchDone(1, 1)
        completeCircuit(1, 2, 1)
    }

    @Test
    fun prs03_statsCountTheWorkDone() {
        openTab("PROGRESS")
        see("3%") // 65 of 1716
        see("65")
        seeDesc("Week 1 day 1, complete")
        seeDesc("Week 1 day 2, 13 of 52")
        see("5/24")
        see("5 completions") // all 13 tie at 5; the three names shown are any of them
        see("MOST DONE")
    }
}
