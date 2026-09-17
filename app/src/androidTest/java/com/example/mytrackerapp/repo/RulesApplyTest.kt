package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.UnitPrefs
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RulesApplyTest {

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
    fun savingInvalidRulesReturnsErrorsAndChangesNothing() = runTest {
        val before = rulesRepo.getDraft()
        val errors = rulesRepo.saveDraft(ProgramRules.DEFAULT.copy(weeks = 0), UnitPrefs())
        assertTrue(errors.isNotEmpty())
        assertEquals(before.weeks, rulesRepo.getDraft().weeks)
    }

    @Test
    fun savingValidRulesChangesTheDraftButNotTheActiveCycle() = runTest {
        val cycleId = repo.ensureActiveCycle()
        val before = rulesRepo.rulesFor(cycleId)

        val errors = rulesRepo.saveDraft(
            ProgramRules.DEFAULT.copy(weeks = 2, circuitsPerWeek = listOf(2, 2)),
            UnitPrefs()
        )
        assertTrue(errors.isEmpty())

        assertEquals(2, rulesRepo.getDraft().weeks)
        // The running cycle's snapshot is unaffected until explicitly applied.
        assertEquals(before.weeks, rulesRepo.rulesFor(cycleId).weeks)
    }

    @Test
    fun applyDraftToCycleChangesTheRunningCycle() = runTest {
        val cycleId = repo.ensureActiveCycle()
        rulesRepo.saveDraft(
            ProgramRules.DEFAULT.copy(weeks = 2, circuitsPerWeek = listOf(2, 2)),
            UnitPrefs()
        )

        val errors = rulesRepo.applyDraftToCycle(cycleId)
        assertTrue(errors.isEmpty())
        assertEquals(2, rulesRepo.rulesFor(cycleId).weeks)
    }

    @Test
    fun restoreDefaultRulesResetsTheDraft() = runTest {
        rulesRepo.saveDraft(
            ProgramRules.DEFAULT.copy(weeks = 2, circuitsPerWeek = listOf(2, 2)),
            UnitPrefs()
        )
        rulesRepo.restoreDefaultRules()

        val draft = rulesRepo.getDraft()
        assertEquals(4, draft.weeks)
        assertEquals(6, draft.daysPerWeek)
        assertEquals(listOf(4, 5, 6, 7), draft.circuitsPerWeek)
    }
}
