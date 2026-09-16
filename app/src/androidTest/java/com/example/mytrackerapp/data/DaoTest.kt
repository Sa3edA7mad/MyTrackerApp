package com.example.mytrackerapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.CompletionDao
import com.example.mytrackerapp.data.db.CycleDao
import com.example.mytrackerapp.data.db.DayDao
import com.example.mytrackerapp.data.entity.CompletionEntity
import com.example.mytrackerapp.data.entity.CycleEntity
import com.example.mytrackerapp.data.entity.DayEntity
import com.example.mytrackerapp.domain.CIRCUIT_STRETCH
import com.example.mytrackerapp.domain.CIRCUIT_WARMUP
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: AppDatabase
    private lateinit var completions: CompletionDao
    private lateinit var cycles: CycleDao
    private lateinit var days: DayDao
    private var cycleId: Long = 0

    @Before
    fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // No seed callback here: this suite wants a clean database it fully controls.
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        completions = db.completionDao()
        cycles = db.cycleDao()
        days = db.dayDao()
        cycleId = cycles.insert(CycleEntity(startedAt = 1_000L))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun completion(
        week: Int = 1,
        day: Int = 1,
        circuit: Int = 1,
        exerciseId: String = "squat"
    ) = CompletionEntity(
        cycleId = cycleId,
        week = week,
        day = day,
        circuit = circuit,
        exerciseId = exerciseId,
        completedAt = 1_000L
    )

    @Test
    fun doubleTapDoesNotDuplicateACompletion() = runTest {
        completions.insert(completion())
        completions.insert(completion())

        assertEquals(1, completions.observeTotalDone(cycleId).first())
    }

    @Test
    fun untickingDeletesTheRow() = runTest {
        completions.insert(completion())
        completions.delete(cycleId, week = 1, day = 1, circuit = 1, exerciseId = "squat")

        assertEquals(0, completions.observeTotalDone(cycleId).first())
    }

    @Test
    fun warmUpAndStretchNeverEnterProgramTotals() = runTest {
        // INVARIANT 2. If this breaks, every count in the app inflates.
        completions.insert(completion(circuit = CIRCUIT_WARMUP, exerciseId = "neck_rolls"))
        completions.insert(completion(circuit = CIRCUIT_STRETCH, exerciseId = "quad_stretch"))
        completions.insert(completion(circuit = 1, exerciseId = "squat"))

        assertEquals(1, completions.observeTotalDone(cycleId).first())

        val circuitCounts = completions.observeCircuitCounts(cycleId).first()
        assertEquals(1, circuitCounts.size)
        assertEquals(1, circuitCounts.single().circuit)

        val dayCounts = completions.observeDayCounts(cycleId).first()
        assertEquals(1, dayCounts.single().done)
    }

    @Test
    fun routineRowsAreStillStoredSoAHalfDoneRoutineResumes() = runTest {
        completions.insert(completion(circuit = CIRCUIT_WARMUP, exerciseId = "neck_rolls"))
        completions.insert(completion(circuit = CIRCUIT_WARMUP, exerciseId = "arm_circles"))

        val ids = completions.getExerciseIdsIn(cycleId, 1, 1, CIRCUIT_WARMUP)
        assertEquals(setOf("neck_rolls", "arm_circles"), ids.toSet())
    }

    @Test
    fun circuitCountsGroupByWeekDayAndCircuit() = runTest {
        completions.insert(completion(week = 1, day = 1, circuit = 1, exerciseId = "squat"))
        completions.insert(completion(week = 1, day = 1, circuit = 1, exerciseId = "push_up"))
        completions.insert(completion(week = 1, day = 1, circuit = 2, exerciseId = "squat"))
        completions.insert(completion(week = 2, day = 3, circuit = 1, exerciseId = "squat"))

        val counts = completions.observeCircuitCounts(cycleId).first()
            .associateBy { Triple(it.week, it.day, it.circuit) }

        assertEquals(3, counts.size)
        assertEquals(2, counts[Triple(1, 1, 1)]?.done)
        assertEquals(1, counts[Triple(1, 1, 2)]?.done)
        assertEquals(1, counts[Triple(2, 3, 1)]?.done)
    }

    @Test
    fun deletingACycleCascadesItsCompletionsAndDays() = runTest {
        completions.insert(completion())
        days.insertIgnore(DayEntity(cycleId = cycleId, week = 1, day = 1))

        db.openHelper.writableDatabase.execSQL("DELETE FROM cycles WHERE id = $cycleId")

        assertEquals(0, completions.getAllForCycle(cycleId).size)
        assertEquals(0, days.observeForCycle(cycleId).first().size)
    }

    @Test
    fun dayRowsAreCreatedOnceAndFlagsPersist() = runTest {
        days.insertIgnore(DayEntity(cycleId = cycleId, week = 1, day = 1))
        val second = days.insertIgnore(DayEntity(cycleId = cycleId, week = 1, day = 1))
        assertEquals("duplicate day insert should be ignored", -1L, second)

        days.markWarmUp(cycleId, 1, 1, ts = 5_000L)
        days.close(cycleId, 1, 1, ts = 6_000L)

        val row = days.get(cycleId, 1, 1)
        assertNotNull(row)
        assertEquals(5_000L, row!!.warmUpDoneAt)
        assertNull(row.stretchDoneAt)
        assertEquals(6_000L, row.closedAt)
    }

    @Test
    fun perExerciseTallyCountsOnlyProgramCircuits() = runTest {
        completions.insert(completion(circuit = 1, exerciseId = "squat"))
        completions.insert(completion(circuit = 2, exerciseId = "squat"))
        completions.insert(completion(circuit = CIRCUIT_WARMUP, exerciseId = "squat"))

        assertEquals(2, completions.observeCountForExercise(cycleId, "squat").first())

        val tallies = completions.observeTallies(cycleId).first()
        assertEquals(1, tallies.size)
        assertEquals("squat", tallies.single().exerciseId)
        assertEquals(2, tallies.single().done)
    }

    @Test
    fun activeCycleLookupReturnsTheOpenOne() = runTest {
        cycles.complete(cycleId, ts = 9_000L)
        val newId = cycles.insert(CycleEntity(startedAt = 10_000L))

        val active = cycles.getActive()
        assertNotNull(active)
        assertEquals(newId, active!!.id)
        assertTrue(active.isActive)
        assertEquals(2, cycles.count())
    }
}
