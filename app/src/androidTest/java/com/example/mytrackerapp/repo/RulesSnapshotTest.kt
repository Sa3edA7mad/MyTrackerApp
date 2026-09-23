package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.data.entity.CycleRulesEntity
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.model.TodayView
import com.example.mytrackerapp.domain.model.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves what the app shows is driven by the `cycle_rules` row, not a compile-time
 * constant — the point of T07/T08.
 */
@RunWith(AndroidJUnit4::class)
class RulesSnapshotTest {

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

    @Test
    fun todayAndProgramFollowAnOverriddenCycleSnapshot() = runTest {
        val cycleId = repo.ensureActiveCycle()

        // Overwrite the snapshot the seed callback wrote: a tiny 2-week, 2-circuit program.
        db.rulesDao().upsertCycleRules(
            CycleRulesEntity(
                cycleId = cycleId,
                weeks = 2,
                daysPerWeek = 6,
                circuitsPerWeekCsv = "2,2",
                exercisesPerCircuit = 13,
                warmUpCount = 8,
                stretchCount = 8,
                dayRolloverHour = 4,
                warmUpEnabled = true,
                stretchEnabled = true,
                countRoutinesInTotals = false,
                lockFutureDays = true,
                programExerciseIdsCsv = "squat",
                snapshotAt = System.currentTimeMillis()
            )
        )

        val today = (repo.observeToday().first { it is UiState.Ready } as UiState.Ready).data
        val day = (today as TodayView.Active).day
        assertEquals(26, day.exercisesTotal) // 2 circuits x 13

        val program = (repo.observeProgram().first { it is UiState.Ready } as UiState.Ready).data
        assertEquals(2, program.size)
    }

    @Test
    fun aFreshCycleIsSnapshottedFromTheDraftImmediately() = runTest {
        val cycleId = repo.ensureActiveCycle()
        val snapshot = db.rulesDao().getCycleRules(cycleId)
        assertEquals(ProgramRules.DEFAULT.weeks, snapshot?.weeks)
        assertEquals("4,5,6,7", snapshot?.circuitsPerWeekCsv)
    }
}
