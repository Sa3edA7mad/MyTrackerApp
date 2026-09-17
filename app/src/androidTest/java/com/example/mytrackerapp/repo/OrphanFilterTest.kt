package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.data.entity.CycleRulesEntity
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
 * INVARIANT 8: a rule change that shrinks the program orphans completions instead of
 * deleting them, and orphans never inflate a total.
 */
@RunWith(AndroidJUnit4::class)
class OrphanFilterTest {

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
    fun shrinkingCircuitsOrphansWithoutDeleting() = runTest {
        val cycleId = repo.ensureActiveCycle()
        val programIds = (db.exerciseDao().getByCategory("BODYWEIGHT") +
            db.exerciseDao().getByCategory("BAND")).map { it.id }

        // Fully complete W1D1 under the default 4-circuit shape: 52 rows.
        for (circuit in 1..4) {
            programIds.forEach { repo.setExerciseDone(1, 1, circuit, it, true) }
        }
        assertEquals(52, db.completionDao().getAllForCycle(cycleId).size)

        // Shrink week 1 to 2 circuits.
        val snapshot = db.rulesDao().getCycleRules(cycleId)!!
        db.rulesDao().upsertCycleRules(snapshot.copy(circuitsPerWeekCsv = "2,5,6,7"))

        // W1D1 was already settled (52/52) before the shrink, so the counter has moved on
        // to W1D2 — read the day's own summary from Program rather than "today".
        val program = (repo.observeProgram().first { it is UiState.Ready } as UiState.Ready).data
        val w1d1 = program.first { it.week == 1 }.days.first { it.day == 1 }
        assertEquals(26, w1d1.done) // only circuits 1-2 count now — circuits 3 and 4 are orphaned
        assertEquals(26, w1d1.total)
        assertTrue("the day is still settled under the new, smaller total", w1d1.isComplete)

        // Nothing was deleted.
        assertEquals(52, db.completionDao().getAllForCycle(cycleId).size)
    }

    @Test
    fun orphanedCompletionCountReportsTheDelta() = runTest {
        val cycleId = repo.ensureActiveCycle()
        val programIds = (db.exerciseDao().getByCategory("BODYWEIGHT") +
            db.exerciseDao().getByCategory("BAND")).map { it.id }
        for (circuit in 1..4) {
            programIds.forEach { repo.setExerciseDone(1, 1, circuit, it, true) }
        }

        val snapshot = db.rulesDao().getCycleRules(cycleId)!!
        db.rulesDao().upsertCycleRules(snapshot.copy(circuitsPerWeekCsv = "2,5,6,7"))

        assertEquals(26, repo.orphanedCompletionCount())
    }
}
