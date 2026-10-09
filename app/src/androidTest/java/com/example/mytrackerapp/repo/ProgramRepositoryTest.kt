package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.CircuitItem
import com.example.mytrackerapp.domain.model.CircuitView
import com.example.mytrackerapp.domain.model.CycleStats
import com.example.mytrackerapp.domain.model.DayState
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
 * Several programs side by side: each runs its own cycle, named circuits and sets, and none
 * can disturb another's totals. Program 2 is the shipped "Gym Strength" starter.
 */
@RunWith(AndroidJUnit4::class)
class ProgramRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var programs: ProgramRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
        programs = ProgramRepository(db.programDao(), db.rulesDao())
    }

    @After
    fun tearDown() = db.close()

    private fun rules(programId: Long) =
        RulesRepository(db.rulesDao(), db.exerciseDao(), db.dayDao(), db.completionDao(), programId)

    private fun repo(programId: Long) = TrackerRepository(
        db.exerciseDao(), db.cycleDao(), db.dayDao(), db.completionDao(), rules(programId), db.resultDao()
    )

    private suspend fun TrackerRepository.today(): DayState =
        ((observeToday().first { it is UiState.Ready } as UiState.Ready).data as TodayView.Active).day

    private suspend fun TrackerRepository.circuit(week: Int, day: Int, index: Int): CircuitView =
        (observeCircuit(week, day, index).first { it is UiState.Ready } as UiState.Ready).data

    private suspend fun TrackerRepository.stats(): CycleStats =
        (observeCycleStats().first { it is UiState.Ready } as UiState.Ready).data

    @Test
    fun aFreshInstallHasHomeActiveAndTheStartersPaused() = runTest {
        assertEquals(
            listOf("Home 4-Week" to true, "Gym Strength" to false, "CrossFit Conditioning" to false),
            programs.observeAll().first().map { it.name to it.active }
        )
        assertEquals(1716, rules(1).getDraft().totalExercisesInCycle())
    }

    @Test
    fun aGymDayRunsItsNamedCircuitAsStraightSets() = runTest {
        val gym = repo(2)
        val day = gym.today()
        assertEquals(1, day.circuits.size)
        assertEquals("Squat + Bench", day.circuits.single().subtitle)
        assertEquals(13, day.circuits.single().total)

        val view = gym.circuit(1, 1, 1)
        assertEquals(13, view.total)
        assertEquals(
            (1..5).map { "barbell_back_squat#$it" } + (1..5).map { "barbell_bench_press#$it" } + (1..3).map { "plank#$it" },
            view.exercises.map { it.id }
        )
        assertEquals("Set 2 of 5", view.setLabels["barbell_back_squat#2"])
        assertEquals(120, view.plan?.restSeconds)
        assertEquals("Deadlift + Pull", gym.circuit(1, 2, 1).plan?.name)
        assertEquals(14, rules(2).getDraft().exercisesPerDay(1, 2))
    }

    @Test
    fun eachSetIsItsOwnTickAndProgramsStayApart() = runTest {
        val gym = repo(2)
        val home = repo(1)
        gym.setExerciseDone(1, 1, 1, "barbell_back_squat#1", true)
        gym.setExerciseDone(1, 1, 1, "barbell_back_squat#2", true)
        gym.setExerciseDone(1, 1, 1, "barbell_back_squat#2", true) // idempotent

        val view = gym.circuit(1, 1, 1)
        assertEquals(setOf("barbell_back_squat#1", "barbell_back_squat#2"), view.doneIds)

        view.exercises.forEach { gym.setExerciseDone(1, 1, 1, it.id, true) }
        assertEquals(1, gym.today().circuitsDone)
        assertEquals(0, home.today().exercisesDone)

        gym.setExerciseDone(1, 1, 1, "plank#3", false)
        assertFalse(gym.circuit(1, 1, 1).isComplete)
    }

    @Test
    fun planEditsWaitForApplyLikeRules() = runTest {
        val gym = repo(2)
        val cycleId = gym.ensureActiveCycle()
        val plan = rules(2).getPlan()
        val a = plan.circuits.first()
        rules(2).savePlan(plan.copy(circuits = listOf(a.copy(items = a.items + CircuitItem("face_pull", 2))) + plan.circuits.drop(1)))

        assertEquals("running cycle keeps its snapshot", 13, gym.circuit(1, 1, 1).total)
        assertEquals(emptyList<String>(), rules(2).applyDraftToCycle(cycleId))
        assertEquals(15, gym.circuit(1, 1, 1).total)
    }

    @Test
    fun aNewProgramCannotGoActiveUntilItsCircuitHasExercises() = runTest {
        val id = programs.create("Travel").getOrThrow()
        assertTrue(programs.setActive(id, true).isFailure)

        val plan = rules(id).getPlan()
        val errors = rules(id).savePlan(
            plan.copy(circuits = listOf(plan.circuits.single().copy(items = listOf(CircuitItem("burpee", 3)))))
        )
        assertEquals(emptyList<String>(), errors)
        assertTrue(programs.setActive(id, true).isSuccess)
        assertTrue(programs.observeActive().first().any { it.id == id })
        assertEquals(3, repo(id).today().exercisesTotal)
    }

    @Test
    fun theStreakCountsTrainingInAnyProgram() = runTest {
        repo(2).setExerciseDone(1, 1, 1, "barbell_back_squat#1", true)
        val home = repo(1).stats()
        assertEquals(1, home.streak)
        assertEquals(0, home.exercisesDone)
    }

    @Test
    fun aTimedCircuitResultIsSavedPerSlot() = runTest {
        val crossfit = repo(3)
        assertEquals(null, crossfit.observeResult(1, 2, 1).first())
        crossfit.saveResult(1, 2, 1, 7)
        crossfit.saveResult(1, 2, 1, 8)
        assertEquals(8, crossfit.observeResult(1, 2, 1).first())
    }

    @Test
    fun theExportCarriesProgramsPlansScoresSetDetailAndCustomExercises() = runTest {
        val gym = repo(2)
        gym.setExerciseDone(
            1, 1, 1, "barbell_back_squat#2", true,
            SetDetail(reps = 5, loadKg = 100.0, rpe = 8, note = "felt good")
        )
        repo(3).saveResult(1, 2, 1, 7)
        val custom = com.example.mytrackerapp.domain.ExerciseDraft(
            name = "Sled Push", category = com.example.mytrackerapp.domain.model.Category.GYM,
            slot = com.example.mytrackerapp.domain.model.ExerciseSlot.LIBRARY, muscles = "", instructions = "",
            targetType = com.example.mytrackerapp.domain.model.TargetType.SECONDS, targetValue = 20,
            perSide = false, targetLabel = "20 sec", videoUrl = ""
        )
        CatalogRepository(db.exerciseDao()).create(custom).getOrThrow()

        val json = org.json.JSONObject(repo(1).exportJson(programs.exportAll()))
        val progs = json.getJSONArray("programs")
        assertEquals(3, progs.length())
        assertTrue(progs.getJSONObject(1).getString("plan").contains("barbell_back_squat"))
        assertEquals("Sled Push", json.getJSONArray("customExercises").getJSONObject(0).getString("name"))

        val cycles = (0 until json.getJSONArray("cycles").length()).map { json.getJSONArray("cycles").getJSONObject(it) }
        val gymCycle = cycles.first { it.getLong("programId") == 2L }
        assertTrue(gymCycle.getString("plan").contains("c:a;"))
        val tick = gymCycle.getJSONArray("completions").getJSONObject(0)
        assertEquals(2, tick.getInt("setNumber"))
        assertEquals(100.0, tick.getDouble("loadKg"), 0.0)
        assertEquals("felt good", tick.getString("note"))
        val crossfit = cycles.first { it.getLong("programId") == 3L }
        assertEquals(7, crossfit.getJSONArray("circuitResults").getJSONObject(0).getInt("value"))
    }

    @Test
    fun archivingPausesAndRestoringStaysPaused() = runTest {
        programs.setActive(2, true).getOrThrow()
        programs.archive(2)
        assertFalse(programs.observeActive().first().any { it.id == 2L })
        programs.restore(2)
        val gym = programs.observeAll().first().first { it.id == 2L }
        assertFalse(gym.archived)
        assertFalse(gym.active)
    }
}
