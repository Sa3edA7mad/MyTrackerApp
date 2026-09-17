package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.CIRCUIT_STRETCH
import com.example.mytrackerapp.domain.CIRCUIT_WARMUP
import com.example.mytrackerapp.domain.Position
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.model.TodayView
import com.example.mytrackerapp.domain.model.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives an entire 1,716-completion cycle through the repository.
 *
 * Nobody is going to hand-test 24 days, and every off-by-one in nextPosition,
 * exercisesPerDay, or the INVARIANT 2 filter surfaces here. It asserts after every single
 * day that the counter advanced to exactly the right place, so a failure names the day.
 */
@RunWith(AndroidJUnit4::class)
class FullCycleTest {

    private val rules = ProgramRules.DEFAULT

    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var programIds: List<String>
    private lateinit var warmUpIds: List<String>
    private lateinit var stretchIds: List<String>

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
        programIds = (db.exerciseDao().getByCategory("BODYWEIGHT") +
                db.exerciseDao().getByCategory("BAND")).map { it.id }
        warmUpIds = db.exerciseDao().getByCategory("WARMUP").map { it.id }
        stretchIds = db.exerciseDao().getByCategory("STRETCH").map { it.id }
        assertEquals(rules.exercisesPerCircuit, programIds.size)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun today(): TodayView =
        (repo.observeToday().first { it is UiState.Ready } as UiState.Ready).data

    private suspend fun activePosition(): Position? =
        (today() as? TodayView.Active)?.day?.let { Position(it.week, it.day) }

    @Test
    fun theWholeCycleCompletesAndTheCounterNeverSlips() = runTest {
        var written = 0

        for (week in 1..rules.weeks) {
            for (day in 1..rules.daysPerWeek) {
                // The counter must be sitting on exactly this day before we start it.
                assertEquals(
                    "counter should be on W$week D$day",
                    Position(week, day),
                    activePosition()
                )

                // Routines run first and must not move any program total.
                val beforeRoutines = (today() as TodayView.Active).day.exercisesDone
                warmUpIds.forEach { repo.setRoutineExerciseDone(CIRCUIT_WARMUP, it, true) }
                repo.markRoutineDone(CIRCUIT_WARMUP)
                assertEquals(
                    "INVARIANT 2 violated on W$week D$day",
                    beforeRoutines,
                    (today() as TodayView.Active).day.exercisesDone
                )

                for (circuit in 1..rules.circuitsForWeek(week)) {
                    programIds.forEach { id ->
                        repo.setExerciseDone(week, day, circuit, id, true)
                        written++
                    }
                }

                stretchIds.forEach { repo.setRoutineExerciseDone(CIRCUIT_STRETCH, it, true) }
                repo.markRoutineDone(CIRCUIT_STRETCH)

                // Day total must be exactly the week's size, routines excluded.
                val isLastDay = week == rules.weeks && day == rules.daysPerWeek
                if (!isLastDay) {
                    val next = activePosition()
                    val expected = if (day < rules.daysPerWeek) {
                        Position(week, day + 1)
                    } else {
                        Position(week + 1, 1)
                    }
                    assertEquals("after W$week D$day", expected, next)
                }
            }
        }

        assertEquals(1716, written)
        assertTrue("cycle should be finished", today() is TodayView.CycleComplete)
    }

    @Test
    fun finishedCycleStatsAddUp() = runTest {
        for (week in 1..rules.weeks) {
            for (day in 1..rules.daysPerWeek) {
                for (circuit in 1..rules.circuitsForWeek(week)) {
                    programIds.forEach { repo.setExerciseDone(week, day, circuit, it, true) }
                }
            }
        }

        val summary = (repo.observeCycleSummary()
            .first { it is UiState.Ready } as UiState.Ready).data
        assertEquals(1716, summary.exercisesDone)
        assertEquals(1716, summary.exercisesTotal)
        assertEquals(132, summary.circuitsDone)
        assertEquals(132, summary.circuitsTotal)
        assertEquals(100, summary.percent)
        assertTrue(summary.isPerfect)
        assertEquals(0, summary.daysClosedEarly)

        val stats = (repo.observeCycleStats()
            .first { it is UiState.Ready } as UiState.Ready).data
        assertEquals(100, stats.percent)
        assertEquals(24, stats.heat.size)
        assertTrue("every day complete", stats.heat.all { it.isComplete })
        assertEquals(132, stats.circuitsDone)

        val program = (repo.observeProgram()
            .first { it is UiState.Ready } as UiState.Ready).data
        assertEquals(listOf(24, 30, 36, 42), program.map { it.circuitsDone })
        assertEquals(132, program.sumOf { it.circuitsDone })
        assertTrue("no week is current once finished", program.none { it.isCurrent })
    }

    @Test
    fun everyDayNeedsExactlyItsOwnNumberOfExercises() = runTest {
        // One short of a full day must NOT advance the counter; the last one must.
        // Only the boundary is asserted — collecting a flow after all 286 writes would
        // turn this into a multi-minute test for no extra coverage.
        for (week in 1..rules.weeks) {
            val size = rules.exercisesPerDay(week)
            var done = 0
            for (circuit in 1..rules.circuitsForWeek(week)) {
                for (id in programIds) {
                    repo.setExerciseDone(week, 1, circuit, id, true)
                    done++
                    if (done == size - 1) {
                        assertEquals(
                            "W$week D1 advanced one exercise short of $size",
                            Position(week, 1),
                            activePosition()
                        )
                    }
                }
            }
            assertEquals(size, done)
            assertEquals("W$week D1 did not settle at $size", Position(week, 2), activePosition())
            // Close the rest of this week so the loop can move to the next one.
            (2..rules.daysPerWeek).forEach { repo.closeDayEarly(week, it) }
        }
        assertTrue(today() is TodayView.CycleComplete)
    }
}
