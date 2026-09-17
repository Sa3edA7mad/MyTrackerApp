package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.model.TodayView
import com.example.mytrackerapp.domain.model.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class CycleRestartTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
        repo = TrackerRepository(
            db.exerciseDao(), db.cycleDao(), db.dayDao(), db.completionDao()
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun todayView(): TodayView {
        val ready = repo.observeToday().first { it is UiState.Ready } as UiState.Ready
        return ready.data
    }

    /** Cheapest route to a finished cycle: close all 24 days early. */
    private suspend fun closeWholeCycle() {
        ProgramRules.DEFAULT.allPositions.forEach { repo.closeDayEarly(it.week, it.day) }
    }

    @Test
    fun closingEveryDayFinishesTheCycle() = runTest {
        closeWholeCycle()
        assertTrue(todayView() is TodayView.CycleComplete)
    }

    @Test
    fun startingANewCycleReturnsToWeekOneDayOne() = runTest {
        repo.setExerciseDone(1, 1, 1, "squat", true)
        closeWholeCycle()
        assertTrue(todayView() is TodayView.CycleComplete)

        repo.startNewCycle()

        val view = todayView()
        assertTrue(view is TodayView.Active)
        val day = (view as TodayView.Active).day
        assertEquals(1, day.week)
        assertEquals(1, day.day)
        assertEquals(0, day.exercisesDone)
        assertEquals(52, day.exercisesLeft)
    }

    @Test
    fun theOldCycleIsClosedAndItsHistoryKept() = runTest {
        repo.setExerciseDone(1, 1, 1, "squat", true)
        repo.setExerciseDone(1, 1, 1, "push_up", true)
        val firstCycleId = repo.ensureActiveCycle()

        closeWholeCycle()
        repo.startNewCycle()

        // Two cycles now, the first closed with a completedAt.
        val all = db.cycleDao().getAll()
        assertEquals(2, all.size)
        val old = all.first { it.id == firstCycleId }
        assertEquals(false, old.isActive)
        assertNotNull("a finished cycle records when it ended", old.completedAt)

        // INVARIANT: completions are never deleted on restart.
        assertEquals(2, db.completionDao().getAllForCycle(firstCycleId).size)

        val active = db.cycleDao().getActive()
        assertNotNull(active)
        assertTrue(active!!.id != firstCycleId)
        assertEquals(0, db.completionDao().getAllForCycle(active.id).size)
    }

    @Test
    fun summaryReportsBestStreakNotTheLiveOne() = runTest {
        repo.setExerciseDone(1, 1, 1, "squat", true)
        val ready = repo.observeCycleSummary().first { it is UiState.Ready } as UiState.Ready
        val summary = ready.data

        assertEquals(1, summary.exercisesDone)
        assertEquals(1716, summary.exercisesTotal)
        assertEquals(132, summary.circuitsTotal)
        assertEquals(1, summary.daysTrained)
        assertEquals(1, summary.bestStreak)
        assertTrue("elapsed is at least one day", summary.elapsedDays >= 1)
        assertEquals(0, summary.percent)
    }

    @Test
    fun summaryCountsDaysClosedEarly() = runTest {
        repo.closeDayEarly(1, 1)
        repo.closeDayEarly(1, 2)

        val ready = repo.observeCycleSummary().first { it is UiState.Ready } as UiState.Ready
        assertEquals(2, ready.data.daysClosedEarly)
    }

    @Test
    fun resettingTheCycleClearsCompletionsButKeepsTheCycle() = runTest {
        val cycleId = repo.ensureActiveCycle()
        repo.setExerciseDone(1, 1, 1, "squat", true)
        repo.closeDayEarly(1, 1)

        repo.resetActiveCycle()

        assertEquals(0, db.completionDao().getAllForCycle(cycleId).size)
        assertEquals(0, db.dayDao().getForCycle(cycleId).size)
        assertEquals(1, db.cycleDao().count())

        val day = (todayView() as TodayView.Active).day
        assertEquals(1, day.week)
        assertEquals(1, day.day)
    }

    @Test
    fun exportProducesParseableJsonCoveringEveryCycle() = runTest {
        repo.setExerciseDone(1, 1, 1, "squat", true)
        repo.setExerciseDone(1, 1, 1, "push_up", true)
        closeWholeCycle()
        repo.startNewCycle()
        repo.setExerciseDone(1, 1, 1, "crunch", true)

        val json = JSONObject(repo.exportJson())
        assertEquals(1, json.getInt("schemaVersion"))

        val cycles = json.getJSONArray("cycles")
        assertEquals("export must cover past cycles too", 2, cycles.length())

        val total = (0 until cycles.length()).sumOf {
            cycles.getJSONObject(it).getJSONArray("completions").length()
        }
        assertEquals(3, total)

        // Spot-check a record's shape.
        val first = cycles.getJSONObject(0).getJSONArray("completions").getJSONObject(0)
        assertTrue(first.has("week"))
        assertTrue(first.has("day"))
        assertTrue(first.has("circuit"))
        assertTrue(first.has("exerciseId"))
        assertTrue(first.has("completedAt"))
    }
}
