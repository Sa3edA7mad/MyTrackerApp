package com.example.mytrackerapp.domain

import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.ExerciseSlot
import com.example.mytrackerapp.domain.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {

    private fun ex(id: String, slot: ExerciseSlot = ExerciseSlot.LIBRARY, sort: Int = 0, enabled: Boolean = true) =
        Exercise(id, id, Category.GYM, "", "", TargetType.REPS, 5, false, "5 reps", "", sort, slot = slot, enabled = enabled)

    private val gym = ProgramPlan(
        circuits = listOf(
            CircuitPlan("a", "Squat; Bench, A", listOf(CircuitItem("squat", 3), CircuitItem("bench", 2, target = 8, loadKg = 62.5))),
            CircuitPlan("b", "B", listOf(CircuitItem("row")), format = CircuitFormat.AMRAP, minutes = 12)
        ),
        days = listOf(listOf("a"), listOf("b"), listOf("a", "b")),
        warmUp = CircuitPlan(ProgramPlan.WARMUP_KEY, "Warm-up", listOf(CircuitItem("cat_cow"))),
        stretch = CircuitPlan(ProgramPlan.STRETCH_KEY, "Stretch", slot = "STRETCH")
    )

    @Test
    fun `codec round-trips every field, including awkward names`() {
        assertEquals(gym, PlanCodec.decode(PlanCodec.encode(gym)))
        assertEquals(ProgramPlan.SLOT_DEFAULT, PlanCodec.decode(PlanCodec.encode(ProgramPlan.SLOT_DEFAULT)))
    }

    @Test
    fun `blank or garbage text decodes to null so callers fall back`() {
        assertNull(PlanCodec.decode(""))
        assertNull(PlanCodec.decode("c:broken"))
    }

    @Test
    fun `resolve expands slots in library order and drops benched or archived items`() {
        val catalog = listOf(
            ex("s2", ExerciseSlot.STRETCH, sort = 2),
            ex("s1", ExerciseSlot.STRETCH, sort = 1),
            ex("off", ExerciseSlot.STRETCH, enabled = false),
            ex("squat"), ex("bench"), ex("cat_cow"),
            ex("row").copy(archivedAt = 1L)
        )
        val resolved = gym.resolve(catalog)
        assertEquals(listOf("s1", "s2"), resolved.stretch.items.map { it.exerciseId })
        assertNull(resolved.stretch.slot)
        assertEquals(emptyList<CircuitItem>(), resolved.circuits[1].items)
        assertEquals(5, resolved.circuits[0].size)
    }

    @Test
    fun `straight sets finish an exercise before the next, rounds interleave`() {
        val catalog = listOf(ex("squat"), ex("bench")).associateBy { it.id }
        val straight = gym.circuits[0].copy(order = CircuitOrder.STRAIGHT).steps(catalog)
        assertEquals(listOf("squat#1", "squat#2", "squat#3", "bench#1", "bench#2"), straight.map { it.key })
        val rounds = gym.circuits[0].steps(catalog)
        assertEquals(listOf("squat#1", "bench#1", "squat#2", "bench#2", "squat#3"), rounds.map { it.key })
    }

    @Test
    fun `a single-set step keys as the bare exercise id, as before sets existed`() {
        val steps = CircuitPlan("x", "X", listOf(CircuitItem("row"))).steps(mapOf("row" to ex("row")))
        assertEquals("row", steps.single().key)
        assertEquals("row" to 1, parseStepKey("row"))
        assertEquals("squat" to 3, parseStepKey("squat#3"))
    }

    @Test
    fun `an override replaces target, label and load and stops weekly progression`() {
        val bench = ex("bench").copy(progressionStep = 2)
        val step = gym.circuits[0].steps(mapOf("bench" to bench)).first()
        assertEquals(8, step.exercise.targetValue)
        assertEquals("8 reps", step.exercise.targetLabel)
        assertEquals(62.5, step.exercise.defaultLoadKg!!, 0.0)
        assertEquals(0, step.exercise.progressionStep)
    }

    @Test
    fun `rules count each day's own rotation of differently sized circuits`() {
        val rules = ProgramRules(
            weeks = 2, daysPerWeek = 3, circuitsPerWeek = listOf(1, 2),
            circuitSizes = listOf(5, 1), dayRotations = gym.dayRotations()
        )
        assertEquals(listOf(5, 1, 6), (1..3).map { rules.exercisesPerDay(1, it) })
        assertEquals(4, rules.circuitsFor(2, 3))
        assertEquals(listOf(0, 1, 0, 1), (1..4).map { rules.planIndexFor(3, it) })
        assertEquals(1, rules.circuitSize(3, 2))
        assertEquals(12 + 24, rules.totalExercisesInCycle())
        assertTrue(rules.isValidSlot(2, 3, 4))
        assertTrue(!rules.isValidSlot(2, 1, 3))
    }

    @Test
    fun `a day with no rotation runs every circuit`() {
        val rules = ProgramRules(daysPerWeek = 2, circuitSizes = listOf(3, 4), circuitsPerWeek = listOf(1, 1, 1, 1))
        assertEquals(listOf(0, 1), rules.rotation(2))
        assertEquals(7, rules.exercisesPerDay(1, 2))
    }

    @Test
    fun `validation catches empty circuits, duplicate exercises and unknown day keys`() {
        assertTrue(PlanValidation.validate(gym).isEmpty())
        val bad = gym.copy(
            circuits = gym.circuits + CircuitPlan("c", "Empty") +
                CircuitPlan("d", "Dupes", listOf(CircuitItem("x"), CircuitItem("x"))),
            days = listOf(listOf("zzz"))
        )
        val errors = PlanValidation.validate(bad)
        assertTrue(errors.any { "Empty has no exercises" in it })
        assertTrue(errors.any { "twice" in it })
        assertTrue(errors.any { "no longer exists" in it })
    }
}
