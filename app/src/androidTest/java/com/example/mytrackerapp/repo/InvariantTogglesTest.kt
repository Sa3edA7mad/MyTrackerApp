package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.CIRCUIT_WARMUP
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
 * INVARIANT 2 and INVARIANT 4 are now toggles rather than laws — this proves both sides
 * of each toggle.
 */
@RunWith(AndroidJUnit4::class)
class InvariantTogglesTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var rulesRepo: RulesRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
        rulesRepo = RulesRepository(db.rulesDao(), db.exerciseDao(), db.dayDao(), db.completionDao())
        repo = TrackerRepository(
            db.exerciseDao(), db.cycleDao(), db.dayDao(), db.completionDao(), rulesRepo
        )
    }

    @After
    fun tearDown() = db.close()

    /**
     * Note: the "Today" ring (DayState) deliberately only ever reflects the numbered
     * program circuits — the toggle's effect is on the day/week/cycle totals that
     * DaySummary and CycleStats compute from [ProgramRules.exercisesPerDay].
     */
    @Test
    fun countingRoutinesAddsWarmUpToTheDayTotal() = runTest {
        val cycleId = repo.ensureActiveCycle()
        val snapshot = db.rulesDao().getCycleRules(cycleId)!!
        db.rulesDao().upsertCycleRules(snapshot.copy(countRoutinesInTotals = true))

        val warmUpIds = db.exerciseDao().getByCategory("WARMUP").map { it.id }
        fun w1d1() = repo.observeProgram()
        val before = (w1d1().first { it is UiState.Ready } as UiState.Ready).data
            .first { it.week == 1 }.days.first { it.day == 1 }
        assertEquals(68, before.total) // 52 program + 8 warm-up + 8 stretch

        warmUpIds.forEach { repo.setRoutineExerciseDone(CIRCUIT_WARMUP, it, true) }

        val after = (w1d1().first { it is UiState.Ready } as UiState.Ready).data
            .first { it.week == 1 }.days.first { it.day == 1 }
        assertEquals(before.done + 8, after.done)
    }

    @Test
    fun turningOffLockFutureDaysMakesEveryDayEditable() = runTest {
        val cycleId = repo.ensureActiveCycle()
        val snapshot = db.rulesDao().getCycleRules(cycleId)!!
        db.rulesDao().upsertCycleRules(snapshot.copy(lockFutureDays = false))

        val circuit = (repo.observeCircuit(4, 6, 1)
            .first { it is UiState.Ready } as UiState.Ready).data
        assertTrue("week 4 day 6 should be editable from week 1 day 1", circuit.editable)
    }

    @Test
    fun futureDaysStayLockedByDefault() = runTest {
        repo.ensureActiveCycle()
        val circuit = (repo.observeCircuit(4, 6, 1)
            .first { it is UiState.Ready } as UiState.Ready).data
        assertTrue("week 4 day 6 should be a locked preview by default", !circuit.editable)
    }
}
