package com.example.mytrackerapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramRulesTest {

    private val default = ProgramRules.DEFAULT

    @Test
    fun `circuits per week`() {
        assertEquals(listOf(4, 5, 6, 7), (1..4).map(default::circuitsForWeek))
    }

    @Test
    fun `exercises per day`() {
        assertEquals(listOf(52, 65, 78, 91), (1..4).map { default.exercisesPerDay(it, 1) })
    }

    @Test
    fun `exercises per week`() {
        assertEquals(listOf(312, 390, 468, 546), (1..4).map(default::exercisesForWeek))
    }

    @Test
    fun `total exercises in cycle`() {
        assertEquals(1716, default.totalExercisesInCycle())
    }

    @Test
    fun `total circuits in cycle`() {
        assertEquals(132, default.totalCircuitsInCycle())
    }

    @Test
    fun `all positions`() {
        assertEquals(24, default.allPositions.size)
        assertEquals(Position(1, 1), default.allPositions.first())
        assertEquals(Position(4, 6), default.allPositions.last())
        assertEquals(Position(2, 1), default.allPositions[6])
    }

    @Test
    fun `circuitsForWeek rejects out of range weeks`() {
        assertThrows(IllegalArgumentException::class.java) { default.circuitsForWeek(0) }
        assertThrows(IllegalArgumentException::class.java) { default.circuitsForWeek(5) }
    }

    @Test
    fun `isValidSlot rejects a circuit beyond the week's count`() {
        assertFalse(default.isValidSlot(1, 1, 5))
    }

    @Test
    fun `isValidSlot accepts warmup when enabled`() {
        assertTrue(default.isValidSlot(1, 1, CIRCUIT_WARMUP))
    }

    @Test
    fun `nextPosition with nothing done is the first position`() {
        assertEquals(Position(1, 1), default.nextPosition(emptyMap(), emptySet(), emptySet()))
    }

    @Test
    fun `nextPosition is null when every day is fully done and stretched`() {
        val done = default.allPositions.associateWith { default.exercisesPerDay(it.week, it.day) }
        assertNull(default.nextPosition(done, default.allPositions.toSet(), emptySet()))
    }

    @Test
    fun `nextPosition is null when every day is closed`() {
        assertNull(default.nextPosition(emptyMap(), emptySet(), default.allPositions.toSet()))
    }

    @Test
    fun `all circuits done but stretch not done does not advance the day`() {
        // Regression: caught live on device. Finishing all 4 circuits of Week 1 Day 1
        // jumped straight to Day 2 without the stretch routine ever being reachable,
        // because settlement originally ignored stretch entirely.
        val done = mapOf(Position(1, 1) to default.exercisesPerDay(1, 1))
        assertEquals(Position(1, 1), default.nextPosition(done, emptySet(), emptySet()))
    }

    @Test
    fun `stretch completion is what releases a fully-exercised day`() {
        val done = mapOf(Position(1, 1) to default.exercisesPerDay(1, 1))
        val stretched = setOf(Position(1, 1))
        assertEquals(Position(1, 2), default.nextPosition(done, stretched, emptySet()))
    }

    @Test
    fun `stretch done alone with no exercises does not settle the day`() {
        assertEquals(
            Position(1, 1),
            default.nextPosition(emptyMap(), setOf(Position(1, 1)), emptySet())
        )
    }

    @Test
    fun `closing a day early releases the counter regardless of stretch`() {
        // INVARIANT 3: without this escape, an abandoned day traps the cycle forever.
        val done = mapOf(Position(1, 1) to 10)
        val closed = setOf(Position(1, 1))
        assertEquals(Position(1, 2), default.nextPosition(done, emptySet(), closed))
    }

    @Test
    fun `isDaySettled requires stretch even when the exercise count is satisfied`() {
        assertFalse(default.isDaySettled(week = 1, day = 1, doneCount = 52, stretchDone = false, closed = false))
        assertTrue(default.isDaySettled(week = 1, day = 1, doneCount = 52, stretchDone = true, closed = false))
    }

    @Test
    fun `isDaySettled closed overrides both the exercise count and the stretch flag`() {
        assertTrue(default.isDaySettled(week = 1, day = 1, doneCount = 0, stretchDone = false, closed = true))
    }

    @Test
    fun `isDaySettled does not require stretch when stretch is disabled`() {
        val rules = default.copy(stretchEnabled = false)
        assertTrue(rules.isDaySettled(week = 1, day = 1, doneCount = 52, stretchDone = false, closed = false))
    }

    @Test
    fun `shrinking the program changes totals`() {
        val rules = default.copy(weeks = 2, circuitsPerWeek = listOf(3, 3))
        assertEquals(12, rules.allPositions.size)
        assertEquals(468, rules.totalExercisesInCycle())
    }

    @Test
    fun `changing exercises per circuit changes exercises per day`() {
        val rules = default.copy(exercisesPerCircuit = 10)
        assertEquals(40, rules.exercisesPerDay(1, 1))
    }

    @Test
    fun `counting routines adds warmup and stretch`() {
        val rules = default.copy(countRoutinesInTotals = true)
        assertEquals(68, rules.exercisesPerDay(1, 1))
    }

    @Test
    fun `counting routines with stretch disabled only adds warmup`() {
        val rules = default.copy(countRoutinesInTotals = true, stretchEnabled = false)
        assertEquals(60, rules.exercisesPerDay(1, 1))
    }

    @Test
    fun `shrinking weeks invalidates a slot in a removed week`() {
        val rules = default.copy(weeks = 3, circuitsPerWeek = listOf(4, 5, 6))
        assertFalse(rules.isValidSlot(4, 1, 1))
    }

    @Test
    fun `circuits csv round trips`() {
        val csv = ProgramRules.circuitsCsv(listOf(4, 5, 6, 7))
        assertEquals(listOf(4, 5, 6, 7), ProgramRules.parseCircuitsCsv(csv))
    }

    @Test
    fun `parseCircuitsCsv ignores blank and non numeric entries`() {
        assertEquals(listOf(4, 5, 7), ProgramRules.parseCircuitsCsv("4, 5,,x,7"))
    }
}
