package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.ExerciseDraft
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.ExerciseSlot
import com.example.mytrackerapp.domain.model.TargetType
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

@RunWith(AndroidJUnit4::class)
class CatalogCrudTest {

    private lateinit var db: AppDatabase
    private lateinit var catalog: CatalogRepository
    private lateinit var repo: TrackerRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
        catalog = CatalogRepository(db.exerciseDao())
        val rulesRepo = RulesRepository(db.rulesDao(), db.exerciseDao(), db.dayDao(), db.completionDao())
        repo = TrackerRepository(
            db.exerciseDao(), db.cycleDao(), db.dayDao(), db.completionDao(), rulesRepo
        )
    }

    @After
    fun tearDown() = db.close()

    private fun draft(name: String = "Bulgarian Split Squat", slot: ExerciseSlot = ExerciseSlot.PROGRAM) =
        ExerciseDraft(
            name = name,
            category = Category.BODYWEIGHT,
            slot = slot,
            muscles = "Legs",
            instructions = "Rear foot elevated.",
            targetType = TargetType.REPS,
            targetValue = 5,
            perSide = true,
            targetLabel = "5 each side",
            videoUrl = ""
        )

    @Test
    fun creatingAnExerciseAddsItToTheCatalogAndTheNextCircuit() = runTest {
        repo.ensureActiveCycle()
        val result = catalog.create(draft())
        assertTrue(result.isSuccess)
        val id = result.getOrThrow()

        val all = catalog.observeAll().first()
        assertTrue(all.any { it.id == id })
    }

    @Test
    fun updatingAnExerciseDoesNotTouchCompletions() = runTest {
        repo.ensureActiveCycle()
        repo.setExerciseDone(1, 1, 1, "squat", true)

        val updated = catalog.update(
            "squat",
            draft(name = "Squat (updated)", slot = ExerciseSlot.PROGRAM).copy(targetValue = 8)
        )
        assertTrue(updated.isSuccess)

        val detail = (repo.observeExerciseDetail("squat")
            .first { it is UiState.Ready } as UiState.Ready).data
        assertEquals(1, detail.totalThisCycle)
        assertEquals("Squat (updated)", detail.exercise.name)
    }

    @Test
    fun archivingRemovesItFromTheCircuitButKeepsHistory() = runTest {
        repo.ensureActiveCycle()
        repo.setExerciseDone(1, 1, 1, "squat", true)

        val result = catalog.archive("squat")
        assertTrue(result.isSuccess)

        assertFalse(catalog.observeAll().first().any { it.id == "squat" })

        val detail = (repo.observeExerciseDetail("squat")
            .first { it is UiState.Ready } as UiState.Ready).data
        assertEquals(1, detail.totalThisCycle)
        assertTrue(detail.exercise.isArchived)
    }

    @Test
    fun restoringBringsItBack() = runTest {
        catalog.archive("squat")
        assertFalse(catalog.observeAll().first().any { it.id == "squat" })

        catalog.restore("squat")
        assertTrue(catalog.observeAll().first().any { it.id == "squat" })
    }

    @Test
    fun archivingTheLastProgramExerciseFails() = runTest {
        val programIds = (db.exerciseDao().getByCategory("BODYWEIGHT") +
            db.exerciseDao().getByCategory("BAND")).map { it.id }
        // Archive all but one.
        programIds.dropLast(1).forEach { assertTrue(catalog.archive(it).isSuccess) }

        val result = catalog.archive(programIds.last())
        assertTrue(result.isFailure)
        assertTrue(catalog.observeAll().first().any { it.id == programIds.last() })
    }

    @Test
    fun moveReordersWithinTheSameSlot() = runTest {
        val before = catalog.observeAll().first()
            .filter { it.slot == ExerciseSlot.PROGRAM }
            .sortedBy { it.sortOrder }
        val first = before[0]
        val second = before[1]

        assertTrue(catalog.move(second.id, -1).isSuccess)

        val after = catalog.observeAll().first()
            .filter { it.slot == ExerciseSlot.PROGRAM }
            .sortedBy { it.sortOrder }
        assertEquals(second.id, after[0].id)
        assertEquals(first.id, after[1].id)
    }
}
