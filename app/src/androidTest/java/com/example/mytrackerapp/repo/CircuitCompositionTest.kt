package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.CIRCUIT_WARMUP
import com.example.mytrackerapp.domain.ExerciseDraft
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.CircuitView
import com.example.mytrackerapp.domain.model.ExerciseSlot
import com.example.mytrackerapp.domain.model.TargetType
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
 * INVARIANT 7 for circuit *contents*: a running cycle keeps the program composition it was
 * snapshotted with. Before this, archiving an exercise mid-cycle removed it from every
 * circuit while the frozen count stayed 13, so no circuit could ever complete again.
 */
@RunWith(AndroidJUnit4::class)
class CircuitCompositionTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var rulesRepo: RulesRepository
    private lateinit var catalog: CatalogRepository

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
        catalog = CatalogRepository(db.exerciseDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun circuit(week: Int = 1, day: Int = 1, index: Int = 1): CircuitView =
        (repo.observeCircuit(week, day, index).first { it is UiState.Ready } as UiState.Ready).data

    private fun newProgramExercise(name: String) = ExerciseDraft(
        name = name, category = Category.BODYWEIGHT, slot = ExerciseSlot.PROGRAM,
        muscles = "", instructions = "", targetType = TargetType.REPS, targetValue = 5,
        perSide = false, targetLabel = "5 reps", videoUrl = ""
    )

    @Test
    fun archivingMidCycleKeepsTheCircuitCompletable() = runTest {
        val cycleId = repo.ensureActiveCycle()
        catalog.archive("squat")

        val view = circuit()
        assertEquals(13, view.total)
        assertTrue(view.exercises.any { it.id == "squat" })

        view.exercises.forEach { repo.setExerciseDone(1, 1, 1, it.id, true) }
        assertTrue(circuit().isComplete)
        val today = (repo.observeToday().first { it is UiState.Ready } as UiState.Ready).data
        assertEquals(1, (today as TodayView.Active).day.circuitsDone)

        rulesRepo.applyDraftToCycle(cycleId)
        assertEquals(12, circuit(index = 2).total)
        assertFalse(circuit(index = 2).exercises.any { it.id == "squat" })
    }

    @Test
    fun anExerciseAddedMidCycleWaitsForApply() = runTest {
        val cycleId = repo.ensureActiveCycle()
        val id = catalog.create(newProgramExercise("Pistol Squat")).getOrThrow()

        assertFalse(circuit().exercises.any { it.id == id })
        assertEquals(13, circuit().total)

        rulesRepo.applyDraftToCycle(cycleId)
        assertTrue(circuit().exercises.any { it.id == id })
        assertEquals(14, circuit().total)
    }

    @Test
    fun theCircuitFollowsTheSnapshotOrder() = runTest {
        val before = circuit().exercises.map { it.id }
        catalog.move("push_up", -1)

        assertEquals("reorder waits for apply", before, circuit().exercises.map { it.id })
    }

    @Test
    fun aDisabledWarmUpMoveLeavesTheRoutine() = runTest {
        catalog.setEnabled("neck_rolls", false)

        val warmUp = (repo.observeRoutine(CIRCUIT_WARMUP).first { it is UiState.Ready } as UiState.Ready).data
        assertEquals(7, warmUp.total)
        assertFalse(warmUp.exercises.any { it.id == "neck_rolls" })
    }
}
