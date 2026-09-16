package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.EXERCISES_PER_CIRCUIT
import com.example.mytrackerapp.domain.circuitsForWeek
import com.example.mytrackerapp.domain.model.CycleStats
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.domain.model.WeekState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ProgramStatsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private val today = LocalDate.of(2026, 9, 16)

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

    private suspend fun program(): List<WeekState> {
        val ready = repo.observeProgram().first { it is UiState.Ready } as UiState.Ready
        return ready.data
    }

    private suspend fun stats(): CycleStats {
        val ready = repo.observeCycleStats { today }
            .first { it is UiState.Ready } as UiState.Ready
        return ready.data
    }

    /** All 13 program exercises for one circuit. */
    private suspend fun completeCircuit(week: Int, day: Int, circuit: Int) {
        val main = db.exerciseDao().getByCategory("BODYWEIGHT") +
                db.exerciseDao().getByCategory("BAND")
        assertEquals(EXERCISES_PER_CIRCUIT, main.size)
        main.forEach { repo.setExerciseDone(week, day, circuit, it.id, true) }
    }

    private suspend fun completeDay(week: Int, day: Int) {
        (1..circuitsForWeek(week)).forEach { completeCircuit(week, day, it) }
    }

    @Test
    fun programHasFourWeeksWithTheRightShape() = runTest {
        val weeks = program()
        assertEquals(4, weeks.size)
        assertEquals(listOf(1, 2, 3, 4), weeks.map { it.week })
        assertEquals(listOf(4, 5, 6, 7), weeks.map { it.circuitsPerDay })
        assertEquals(listOf(24, 30, 36, 42), weeks.map { it.circuitsTotal })
        assertEquals(132, weeks.sumOf { it.circuitsTotal })
        assertTrue(weeks.all { it.days.size == 6 })
    }

    @Test
    fun eachDayCarriesItsPerCircuitBreakdown() = runTest {
        val weeks = program()
        assertEquals(4, weeks[0].days.first().circuits.size)
        assertEquals(7, weeks[3].days.first().circuits.size)
        assertEquals(listOf(1, 2, 3, 4), weeks[0].days.first().circuits.map { it.index })
    }

    @Test
    fun onlyTheCurrentWeekIsFlagged() = runTest {
        assertEquals(listOf(true, false, false, false), program().map { it.isCurrent })

        // Finish all of week 1 and the flag should move to week 2.
        (1..6).forEach { completeDay(1, it) }
        assertEquals(listOf(false, true, false, false), program().map { it.isCurrent })
    }

    @Test
    fun completingOneCircuitShowsUpInTheBreakdown() = runTest {
        completeCircuit(1, 1, 1)

        val day = program().first().days.first()
        assertEquals(13, day.done)
        assertTrue(day.isPartial)
        assertFalse(day.isComplete)
        assertEquals(13, day.circuits.first { it.index == 1 }.done)
        assertEquals(0, day.circuits.first { it.index == 2 }.done)
    }

    @Test
    fun statsStartEmpty() = runTest {
        val s = stats()
        assertEquals(0, s.streak)
        assertEquals(0, s.exercisesDone)
        assertEquals(0, s.percent)
        assertEquals(1716, s.exercisesTotal)
        assertEquals(132, s.circuitsTotal)
        assertEquals(24, s.heat.size)
        assertTrue(s.heat.all { it.isUntouched })
        assertTrue(s.mostDone.isEmpty())
    }

    @Test
    fun aCompleteWeekOneReadsEighteenPercent() = runTest {
        (1..6).forEach { completeDay(1, it) }

        val s = stats()
        assertEquals(312, s.exercisesDone)
        assertEquals(18, s.percent)
        assertEquals(24, s.circuitsDone)
        assertEquals(6, s.heat.count { it.isComplete })
        assertTrue(s.heat.filter { it.week == 1 }.all { it.isComplete })
        assertTrue(s.heat.filter { it.week > 1 }.all { it.isUntouched })
    }

    @Test
    fun heatMapDistinguishesPartialFromComplete() = runTest {
        completeCircuit(1, 1, 1) // 13 of 52
        completeDay(1, 2)        // all 52

        val s = stats()
        val d1 = s.heat.first { it.week == 1 && it.day == 1 }
        val d2 = s.heat.first { it.week == 1 && it.day == 2 }
        assertTrue(d1.isPartial)
        assertFalse(d1.isComplete)
        assertTrue(d2.isComplete)
    }

    @Test
    fun mostDoneRanksTheBusiestExercises() = runTest {
        completeCircuit(1, 1, 1)
        completeCircuit(1, 1, 2)
        // squat gets a third completion, putting it clear of the rest.
        repo.setExerciseDone(1, 1, 3, "squat", true)

        val s = stats()
        assertEquals("squat", s.mostDone.first().exerciseId)
        assertEquals("Squat", s.mostDone.first().name)
        assertEquals(3, s.mostDone.first().count)
        assertTrue(s.mostDone.size <= 3)
    }

    @Test
    fun aClosedDayCountsAsTrainedButNotComplete() = runTest {
        completeCircuit(1, 1, 1)
        repo.closeDayEarly(1, 1)

        val s = stats()
        val d1 = s.heat.first { it.week == 1 && it.day == 1 }
        assertTrue(d1.closed)
        assertFalse(d1.isComplete)
        assertTrue(d1.isPartial)

        // And the program counter has moved on to day 2.
        assertEquals(listOf(true, false, false, false), program().map { it.isCurrent })
        val nextDay = program().first().days.first { it.day == 2 }
        assertEquals(0, nextDay.done)
    }
}
