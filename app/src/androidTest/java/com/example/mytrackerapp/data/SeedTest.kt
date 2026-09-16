package com.example.mytrackerapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the verbatim transcription of Saeed_4Week.xlsx. These assertions fail if the
 * middle dots or em dashes get mangled by an encoding slip, which is otherwise a very
 * quiet kind of corruption.
 */
@RunWith(AndroidJUnit4::class)
class SeedTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun catalogHasTwentyNineRowsInTheRightCategories() = runTest {
        val all = db.exerciseDao().getAll()
        assertEquals(29, all.size)

        val byCategory = all.groupingBy { it.category }.eachCount()
        assertEquals(6, byCategory["BODYWEIGHT"])
        assertEquals(7, byCategory["BAND"])
        assertEquals(8, byCategory["WARMUP"])
        assertEquals(8, byCategory["STRETCH"])
    }

    @Test
    fun firstCycleIsOpenOnCreate() = runTest {
        // INVARIANT 1: a fresh install must already have an active cycle to read.
        val active = db.cycleDao().getActive()
        assertNotNull("a fresh database must open a cycle", active)
        assertTrue(active!!.isActive)
        assertEquals(1, db.cycleDao().count())
    }

    @Test
    fun middleDotSurvivedTranscription() = runTest {
        val squat = db.exerciseDao().getById("squat")
        assertNotNull(squat)
        assertEquals("Legs · Glutes · Core", squat!!.muscles)
    }

    @Test
    fun emDashSurvivedTranscription() = runTest {
        val crunch = db.exerciseDao().getById("crunch")
        assertNotNull(crunch)
        assertTrue(
            "Crunch instructions should contain an em dash",
            crunch!!.instructions.contains('—')
        )
    }

    @Test
    fun apostropheSurvivedTranscription() = runTest {
        val pose = db.exerciseDao().getById("childs_pose")
        assertNotNull(pose)
        assertEquals("Child's Pose", pose!!.name)
    }

    @Test
    fun perSideTargetsAreFlagged() = runTest {
        val perSide = db.exerciseDao().getAll().filter { it.perSide }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "external_rotation", "internal_rotation",
                "quad_stretch", "hamstring_stretch", "hip_flexor_stretch",
                "lat_stretch", "shoulder_cross_band", "spinal_twist"
            ),
            perSide
        )
        assertEquals("10 reps each side", db.exerciseDao().getById("external_rotation")!!.targetLabel)
    }

    @Test
    fun timedHoldsCarryTheirDuration() = runTest {
        assertEquals(15, db.exerciseDao().getById("dead_hang")!!.targetValue)
        assertEquals("SECONDS", db.exerciseDao().getById("dead_hang")!!.targetType)
        assertEquals(45, db.exerciseDao().getById("childs_pose")!!.targetValue)
        // Every stretch is a timed hold.
        assertTrue(db.exerciseDao().getByCategory("STRETCH").all { it.targetType == "SECONDS" })
        // Every warm-up move is self-paced reps.
        assertTrue(db.exerciseDao().getByCategory("WARMUP").all { it.targetType == "REPS" })
    }

    @Test
    fun everyExerciseHasAYouTubeLink() = runTest {
        val all = db.exerciseDao().getAll()
        assertTrue(all.all { it.videoUrl.startsWith("https://www.youtube.com/") })
        // The four real videos from the sheet, as opposed to search URLs.
        assertEquals(
            "https://www.youtube.com/watch?v=LSkyinhmA8k",
            all.single { it.id == "band_row" }.videoUrl
        )
        assertEquals(4, all.count { it.videoUrl.contains("/watch?v=") })
        assertEquals(25, all.count { it.videoUrl.contains("/results?search_query=") })
    }

    @Test
    fun warmUpMovesHaveNoMuscleColumn() = runTest {
        // The workbook leaves this blank for warm-up; we do not invent data.
        assertTrue(db.exerciseDao().getByCategory("WARMUP").all { it.muscles.isEmpty() })
        assertTrue(db.exerciseDao().getByCategory("BODYWEIGHT").none { it.muscles.isEmpty() })
    }

    @Test
    fun sortOrderIsStableAndBanded() = runTest {
        val all = db.exerciseDao().getAll()
        assertEquals(all.map { it.sortOrder }, all.map { it.sortOrder }.sorted())
        assertEquals("squat", all.first().id)
        assertEquals("spinal_twist", all.last().id)
    }
}
