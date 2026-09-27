package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.CIRCUIT_STRETCH
import com.example.mytrackerapp.domain.model.CircuitView
import com.example.mytrackerapp.domain.model.DayState
import com.example.mytrackerapp.domain.model.TodayView
import com.example.mytrackerapp.domain.model.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finishing a day's last circuit used to move Today straight to the next day, so the
 * "Finish with stretching" step was unreachable and a stretch done afterwards landed on the
 * wrong day. A day now settles only once its stretch is done or skipped (INVARIANT 3,
 * [com.example.mytrackerapp.domain.ProgramRules.isDaySettled]); these check it end to end
 * through the repository.
 */
@RunWith(AndroidJUnit4::class)
class StretchHoldTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var programIds: List<String>

    @Before
    fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
        val rulesRepo = RulesRepository(
            db.rulesDao(), db.exerciseDao(), db.dayDao(), db.completionDao()
        )
        repo = TrackerRepository(
            db.exerciseDao(), db.cycleDao(), db.dayDao(), db.completionDao(), rulesRepo
        )
        programIds = db.exerciseDao().getAll().filter { it.slot == "PROGRAM" }.map { it.id }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun today(): DayState {
        val ready = repo.observeToday().first { it is UiState.Ready } as UiState.Ready
        return (ready.data as TodayView.Active).day
    }

    private suspend fun routine(circuit: Int): CircuitView =
        (repo.observeRoutine(circuit).first { it is UiState.Ready } as UiState.Ready).data

    private suspend fun finishCircuits(week: Int, day: Int, circuits: Int) {
        for (c in 1..circuits) programIds.forEach { repo.setExerciseDone(week, day, c, it, true) }
    }

    @Test
    fun finishingTheLastCircuitKeepsTodayOnThatDay() = runTest {
        finishCircuits(1, 1, 4)

        val day = today()
        assertEquals(1, day.day)
        assertTrue(day.allCircuitsComplete)
        assertTrue(day.stretchPending)
        // Not settled until stretched, so Program's "you are here" stays too.
        assertEquals(1, repo.observeCurrentPosition().first()?.day)
    }

    @Test
    fun theStretchLandsOnTheDayItBelongsTo() = runTest {
        finishCircuits(1, 1, 4)

        val stretch = routine(CIRCUIT_STRETCH)
        assertEquals(1, stretch.day)

        repo.markRoutineDone(CIRCUIT_STRETCH)
        val day1 = db.dayDao().get(1, 1, 1)
        assertTrue("stretch flag is on day 1", day1?.stretchDoneAt != null)
        assertEquals(2, today().day)
    }

    @Test
    fun skippingTheStretchMovesOn() = runTest {
        finishCircuits(1, 1, 4)
        repo.markStretchDone(1, 1)

        val day = today()
        assertEquals(2, day.day)
        assertFalse(day.stretchDone)
    }

    @Test
    fun tickingTheNextDayFromTheListDoesNotSkipTheStretch() = runTest {
        finishCircuits(1, 1, 4)
        // List view can tick a locked future day; that must not settle the unstretched one.
        repo.setExerciseDone(1, 2, 1, programIds.first(), true)

        assertEquals(1, today().day)
        assertEquals(1, routine(CIRCUIT_STRETCH).day)
    }

    @Test
    fun aDayEndedEarlyIsNotHeld() = runTest {
        finishCircuits(1, 1, 2)
        repo.closeDayEarly(1, 1)

        assertEquals(2, today().day)
    }
}
