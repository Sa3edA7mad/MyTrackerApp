package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetDetailTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrackerRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
        val rulesRepo = RulesRepository(db.rulesDao(), db.exerciseDao(), db.dayDao(), db.completionDao())
        repo = TrackerRepository(
            db.exerciseDao(), db.cycleDao(), db.dayDao(), db.completionDao(), rulesRepo
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun tickingWithDetailWritesItAndReadsItBack() = runTest {
        repo.ensureActiveCycle()
        repo.setExerciseDone(1, 1, 1, "squat", true, SetDetail(reps = 8, loadKg = 20.0, rpe = 7))

        val last = repo.lastDetailFor("squat")
        assertEquals(8, last?.reps)
        assertEquals(20.0, last?.loadKg)
        assertEquals(7, last?.rpe)
    }

    @Test
    fun unTickingAndRetickingLosesTheOldDetail() = runTest {
        repo.ensureActiveCycle()
        repo.setExerciseDone(1, 1, 1, "squat", true, SetDetail(reps = 8, loadKg = 20.0))
        repo.setExerciseDone(1, 1, 1, "squat", false)
        // Re-tick with no detail — presence of the row is the truth, not a memory of the old detail.
        repo.setExerciseDone(1, 1, 1, "squat", true)

        assertNull(repo.lastDetailFor("squat"))
    }

    @Test
    fun lastDetailForReturnsTheMostRecentLoggedRow() = runTest {
        repo.ensureActiveCycle()
        repo.setExerciseDone(1, 1, 1, "squat", true, SetDetail(reps = 5, loadKg = 15.0))
        repo.setExerciseDone(1, 1, 2, "squat", true, SetDetail(reps = 8, loadKg = 22.5))

        val last = repo.lastDetailFor("squat")
        assertEquals(8, last?.reps)
        assertEquals(22.5, last?.loadKg)
    }

    @Test
    fun observePerformanceReportsTheBestLoad() = runTest {
        repo.ensureActiveCycle()
        repo.setExerciseDone(1, 1, 1, "squat", true, SetDetail(reps = 8, loadKg = 20.0))
        repo.setExerciseDone(1, 1, 2, "squat", true, SetDetail(reps = 8, loadKg = 25.0))

        val summary = repo.observePerformance("squat").first()
        assertEquals(25.0, summary.bestLoadKg)
        assertEquals(2, summary.loggedSets)
    }

    @Test
    fun updateSetDetailChangesAnAlreadyTickedRow() = runTest {
        repo.ensureActiveCycle()
        repo.setExerciseDone(1, 1, 1, "squat", true)
        repo.updateSetDetail(1, 1, 1, "squat", SetDetail(reps = 10, loadKg = 30.0))

        val last = repo.lastDetailFor("squat")
        assertEquals(10, last?.reps)
        assertEquals(30.0, last?.loadKg)
    }
}
