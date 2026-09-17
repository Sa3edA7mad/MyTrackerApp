package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T13: exercisesPerCircuit is derived from the live catalog for the *draft*, but the
 * active cycle keeps reading its own frozen snapshot until the draft is explicitly applied.
 */
@RunWith(AndroidJUnit4::class)
class CatalogRulesTest {

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
    fun archivingAProgramExerciseShrinksTheDraftButNotTheRunningCycle() = runTest {
        val cycleId = repo.ensureActiveCycle()
        assertEquals(13, rulesRepo.getDraft().exercisesPerCircuit)
        assertEquals(13, rulesRepo.rulesFor(cycleId).exercisesPerCircuit)

        db.exerciseDao().archive("squat", System.currentTimeMillis())

        assertEquals(12, rulesRepo.getDraft().exercisesPerCircuit)
        assertEquals("running cycle keeps its frozen count", 13, rulesRepo.rulesFor(cycleId).exercisesPerCircuit)

        rulesRepo.applyDraftToCycle(cycleId)
        assertEquals(12, rulesRepo.rulesFor(cycleId).exercisesPerCircuit)
    }
}
