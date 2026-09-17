package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.CIRCUIT_WARMUP
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.ExerciseDetail
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
class ExerciseDetailRepositoryTest {

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

    private suspend fun detail(id: String): ExerciseDetail {
        val ready = repo.observeExerciseDetail(id).first { it is UiState.Ready } as UiState.Ready
        return ready.data
    }

    @Test
    fun catalogFieldsSurviveVerbatim() = runTest {
        val d = detail("band_row")
        assertEquals("Band Row", d.exercise.name)
        assertEquals(Category.BAND, d.exercise.category)
        assertEquals("Back · Biceps · Rear Delts", d.exercise.muscles)
        assertEquals(TargetType.REPS, d.exercise.targetType)
        assertEquals(10, d.exercise.targetValue)
        assertEquals("10 reps", d.exercise.targetLabel)
        assertEquals("https://www.youtube.com/watch?v=LSkyinhmA8k", d.exercise.videoUrl)
        assertTrue(d.exercise.instructions.startsWith("Anchor band at waist height."))
    }

    @Test
    fun anUntouchedExerciseHasNoHistory() = runTest {
        val d = detail("squat")
        assertEquals(0, d.totalThisCycle)
        assertFalse(d.hasHistory)
        assertTrue(d.recent.isEmpty())
        assertEquals(0, d.maxCount)
    }

    @Test
    fun programCompletionsAccumulateAcrossCircuits() = runTest {
        repo.setExerciseDone(1, 1, 1, "squat", true)
        repo.setExerciseDone(1, 1, 2, "squat", true)
        repo.setExerciseDone(1, 1, 3, "squat", true)

        val d = detail("squat")
        assertEquals(3, d.totalThisCycle)
        assertTrue(d.hasHistory)
        // All three landed on the same training date, so one bar of height 3.
        assertEquals(1, d.recent.size)
        assertEquals(3, d.recent.single().count)
        assertEquals(3, d.maxCount)
    }

    @Test
    fun untickingRemovesItFromHistory() = runTest {
        repo.setExerciseDone(1, 1, 1, "squat", true)
        assertEquals(1, detail("squat").totalThisCycle)

        repo.setExerciseDone(1, 1, 1, "squat", false)
        assertEquals(0, detail("squat").totalThisCycle)
    }

    /**
     * The documented exception to INVARIANT 2: per-exercise history is unfiltered so a
     * warm-up move can show a real count instead of always reading zero. This must not
     * leak into day totals, which RoutineRepositoryTest covers.
     */
    @Test
    fun warmUpMovesShowTheirOwnRoutineHistory() = runTest {
        repo.setRoutineExerciseDone(CIRCUIT_WARMUP, "neck_rolls", true)
        repo.setRoutineExerciseDone(CIRCUIT_WARMUP, "cat_cow", true)

        assertEquals(1, detail("neck_rolls").totalThisCycle)
        assertEquals(1, detail("cat_cow").totalThisCycle)
        assertEquals(0, detail("arm_circles").totalThisCycle)
    }

    @Test
    fun anUnknownIdSurfacesAnError() = runTest {
        val state = repo.observeExerciseDetail("not_a_real_exercise")
            .first { it !is UiState.Loading }
        assertTrue(state is UiState.Error)
    }

    @Test
    fun everyCatalogEntryResolves() = runTest {
        db.exerciseDao().getAll().forEach { row ->
            val d = detail(row.id)
            assertEquals(row.id, d.exercise.id)
            assertEquals(row.name, d.exercise.name)
        }
    }
}
