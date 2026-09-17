package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.CIRCUIT_STRETCH
import com.example.mytrackerapp.domain.CIRCUIT_WARMUP
import com.example.mytrackerapp.domain.model.Category
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
 * Guards INVARIANT 2 at the repository level.
 *
 * This is the gate for G11 rather than a UI walk-through: whether warm-up completions
 * leak into the day total is a data property, and asserting it here is both stricter and
 * immune to emulator timing.
 */
@RunWith(AndroidJUnit4::class)
class RoutineRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository

    @Before
    fun setUp() {
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
    }

    @After
    fun tearDown() = db.close()

    private suspend fun today(): DayState {
        val ready = repo.observeToday().first { it is UiState.Ready } as UiState.Ready
        return (ready.data as TodayView.Active).day
    }

    private suspend fun routine(circuit: Int): CircuitView {
        val ready = repo.observeRoutine(circuit).first { it is UiState.Ready } as UiState.Ready
        return ready.data
    }

    @Test
    fun routineResolvesTheCurrentDayAndItsOwnCatalogSlice() = runTest {
        val warmUp = routine(CIRCUIT_WARMUP)
        assertEquals(1, warmUp.week)
        assertEquals(1, warmUp.day)
        assertEquals(8, warmUp.exercises.size)
        assertTrue(warmUp.exercises.all { it.category == Category.WARMUP })
        assertEquals("neck_rolls", warmUp.exercises.first().id)

        val stretch = routine(CIRCUIT_STRETCH)
        assertEquals(8, stretch.exercises.size)
        assertTrue(stretch.exercises.all { it.category == Category.STRETCH })
        assertTrue("every stretch is a timed hold", stretch.exercises.all { it.targetValue > 0 })
    }

    @Test
    fun warmUpCompletionsNeverCountTowardTheDayTotal() = runTest {
        // INVARIANT 2. If this breaks, Today reads "8 done / 44 left" before the user has
        // performed a single program exercise.
        val warmUps = db.exerciseDao().getByCategory("WARMUP")
        assertEquals(8, warmUps.size)
        warmUps.forEach { repo.setRoutineExerciseDone(CIRCUIT_WARMUP, it.id, true) }
        repo.markRoutineDone(CIRCUIT_WARMUP)

        val day = today()
        assertEquals(1, day.week)
        assertEquals(1, day.day)
        assertEquals("warm-up must not count", 0, day.exercisesDone)
        assertEquals(52, day.exercisesLeft)
        assertEquals(52, day.exercisesTotal)
        assertEquals(0, day.circuitsDone)
        assertEquals(4, day.circuitsTotal)
        assertTrue("day flag should be set", day.warmUpDone)
        assertFalse(day.stretchDone)
    }

    @Test
    fun stretchCompletionsNeverCountEither() = runTest {
        db.exerciseDao().getByCategory("STRETCH").forEach {
            repo.setRoutineExerciseDone(CIRCUIT_STRETCH, it.id, true)
        }
        repo.markRoutineDone(CIRCUIT_STRETCH)

        val day = today()
        assertEquals(0, day.exercisesDone)
        assertTrue(day.stretchDone)
        assertFalse(day.warmUpDone)
    }

    @Test
    fun aHalfDoneRoutineResumesWhereItLeftOff() = runTest {
        repo.setRoutineExerciseDone(CIRCUIT_WARMUP, "neck_rolls", true)
        repo.setRoutineExerciseDone(CIRCUIT_WARMUP, "arm_circles", true)

        val warmUp = routine(CIRCUIT_WARMUP)
        assertEquals(setOf("neck_rolls", "arm_circles"), warmUp.doneIds)
        assertEquals(2, warmUp.done)
        // Neck Rolls is index 0 and is done; Shoulder Rolls at index 1 is not.
        assertEquals(1, warmUp.firstUndoneIndex)
    }

    @Test
    fun skippingARoutineSetsTheFlagWithoutWritingCompletions() = runTest {
        repo.markRoutineDone(CIRCUIT_WARMUP)

        val day = today()
        assertTrue(day.warmUpDone)
        assertEquals(0, day.exercisesDone)
        assertEquals(0, routine(CIRCUIT_WARMUP).done)
    }

    @Test
    fun warmUpAloneNeverSettlesTheDay() = runTest {
        db.exerciseDao().getByCategory("WARMUP").forEach {
            repo.setRoutineExerciseDone(CIRCUIT_WARMUP, it.id, true)
        }
        repo.markRoutineDone(CIRCUIT_WARMUP)

        // Still week 1 day 1 — the counter must not advance on a warm-up.
        val day = today()
        assertEquals(1, day.week)
        assertEquals(1, day.day)
    }

    @Test
    fun routineFollowsTheCounterToTheNextDay() = runTest {
        // Close day 1 early, then the routine should resolve to day 2.
        repo.closeDayEarly(1, 1)

        val warmUp = routine(CIRCUIT_WARMUP)
        assertEquals(1, warmUp.week)
        assertEquals(2, warmUp.day)

        // And a flag set now lands on day 2, not day 1.
        repo.markRoutineDone(CIRCUIT_WARMUP)
        val day = today()
        assertEquals(2, day.day)
        assertTrue(day.warmUpDone)
    }

    @Test
    fun programCircuitsStillCountNormally() = runTest {
        // Sanity check that the circuit >= 1 filter does not exclude real work.
        val main = db.exerciseDao().getByCategory("BODYWEIGHT") +
                db.exerciseDao().getByCategory("BAND")
        assertEquals(13, main.size)
        main.forEach { repo.setExerciseDone(1, 1, 1, it.id, true) }

        val day = today()
        assertEquals(13, day.exercisesDone)
        assertEquals(39, day.exercisesLeft)
        assertEquals(1, day.circuitsDone)
        assertEquals(2, day.nextCircuit)
    }
}
