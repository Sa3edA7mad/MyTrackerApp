package com.example.mytrackerapp.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** TOD-xx in docs/FEATURES.md. */
@RunWith(AndroidJUnit4::class)
class TodayE2eTest : E2eTest() {

    @Test
    fun tod01_freshInstallOpensOnWeekOneDayOne() {
        see("WEEK 1 · DAY 1")
        see("4 circuits · 13 exercises each")
        seeDesc("0 of 4 circuits complete")
        see(" exercises done")
        see("52")
        see("8 moves · before your first circuit")
        (1..4).forEach { see("Circuit $it") }
        see("0/13")
        see("8 stretches · after your last circuit")
        seeButton("Warm up, then circuit 1")
    }

    @Test
    fun tod02_warmUpCtaOpensWarmUpAndSkipMarksItDone() {
        tapButton("Warm up, then circuit 1")
        see("WARM-UP · WEEK 1 DAY 1")
        see("1/8")
        see("Neck Rolls")
        tapButton("Skip warm-up")
        see("8 moves · done")
        seeButton("Start circuit 1")
    }

    @Test
    fun tod03_startCircuitCtaOpensTheNextIncompleteCircuit() {
        tapButton("Warm up, then circuit 1")
        tapButton("Skip warm-up")
        tapButton("Start circuit 1")
        see("CIRCUIT 1 · WEEK 1 DAY 1")
        see("Squat")
    }

    @Test
    fun tod04_endDayEarlyAsksThenMovesOn() {
        tapDesc("End day early")
        see("End day early?")
        see("Circuits 1, 2, 3, 4 will stay incomplete", substring = true)
        tapText("KEEP GOING")
        see("WEEK 1 · DAY 1")

        tapDesc("End day early")
        tapText("END DAY")
        see("WEEK 1 · DAY 2")
    }

    @Test
    fun tod05_circuitCardsReflectProgress() {
        tapText("Circuit 2")
        tapButton("✓  Done")
        tapDesc("Close circuit")
        see("1/13")
        see("51") // left today
        seeDesc("0 of 4 circuits complete")
    }
}

/** TOD-06..08: the finished-day stretch hold (regression for the unreachable stretch step). */
@RunWith(AndroidJUnit4::class)
class TodayStretchHoldE2eTest : E2eTest() {

    override suspend fun arrange() {
        container.repo.markWarmUpDone(1, 1)
        completeDay(1, 1)
    }

    @Test
    fun tod06_aFinishedDayWaitsForItsStretch() {
        see("WEEK 1 · DAY 1")
        seeDesc("4 of 4 circuits complete")
        see("8 stretches · finish your day")
        seeButton("Finish with stretching")
        seeButton("Skip stretching")
    }

    @Test
    fun tod07_skipStretchingMovesToTheNextDay() {
        tapButton("Skip stretching")
        see("WEEK 1 · DAY 2")
        seeButton("Warm up, then circuit 1")
    }

    @Test
    fun tod08_stretchingIsRecordedOnTheFinishedDay() {
        tapButton("Finish with stretching")
        see("STRETCH · WEEK 1 DAY 1")
        see("Quad Stretch")
        tapButton("Skip stretch")
        see("WEEK 1 · DAY 2")
    }
}

/** TOD-09: disabled routines disappear from Today. */
@RunWith(AndroidJUnit4::class)
class TodayRoutinesOffE2eTest : E2eTest() {

    override suspend fun arrange() {
        applyRules { it.copy(warmUpEnabled = false, stretchEnabled = false) }
    }

    @Test
    fun tod09_withRoutinesOffTodayGoesStraightToCircuits() {
        seeButton("Start circuit 1")
        dontSee("WARM-UP")
        dontSee("STRETCH")
        dontSee("Warm up, then circuit 1")
    }
}

/** TOD-10..12 and CYC-xx: end of cycle. */
@RunWith(AndroidJUnit4::class)
class CycleCompleteE2eTest : E2eTest() {

    override suspend fun arrange() {
        completeDay(1, 1)
        container.repo.markStretchDone(1, 1)
        for (week in 1..4) for (day in 1..6) {
            if (week == 1 && day == 1) continue
            container.repo.closeDayEarly(week, day)
        }
    }

    @Test
    fun cyc01_todayShowsTheFinishedState() {
        see("CYCLE COMPLETE")
        see("Nothing left to tick")
        see("All 24 days of this cycle are done", substring = true)
    }

    @Test
    fun cyc02_summaryReportsTheCycle() {
        tapButton("See cycle summary")
        see("Four weeks done")
        seeDesc("3 percent of the cycle completed")
        see("52")
        see("EXERCISES")
        see("DAYS TRAINED")
        see("BEST STREAK")
        see("DAYS ELAPSED")
        see("with 23 days ended early", substring = true)
    }

    @Test
    fun cyc03_notYetReturnsWithoutStartingANewCycle() {
        tapButton("See cycle summary")
        tapButton("Not yet")
        see("Nothing left to tick")
    }

    @Test
    fun cyc04_startingANewCycleReturnsToWeekOneDayOne() {
        tapButton("See cycle summary")
        tapButton("Start a new cycle")
        see("WEEK 1 · DAY 1")
        seeDesc("0 of 4 circuits complete")
        openTab("PROGRESS")
        see("0%")
    }
}
