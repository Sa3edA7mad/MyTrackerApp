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
        assertEquals(listOf(52, 65, 78, 91), (1..4).map(default::exercisesPerDay))
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
        assertEquals(Position(1, 1), default.nextPosition(emptyMap(), emptySet()))
    }

    @Test
    fun `nextPosition is null when every day is fully done`() {
        val done = default.allPositions.associateWith { default.exercisesPerDay(it.week) }
        assertNull(default.nextPosition(done, emptySet()))
    }

    @Test
    fun `nextPosition is null when every day is closed`() {
        assertNull(default.nextPosition(emptyMap(), default.allPositions.toSet()))
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
        assertEquals(40, rules.exercisesPerDay(1))
    }

    @Test
    fun `counting routines adds warmup and stretch`() {
        val rules = default.copy(countRoutinesInTotals = true)
        assertEquals(68, rules.exercisesPerDay(1))
    }

    @Test
    fun `counting routines with stretch disabled only adds warmup`() {
        val rules = default.copy(countRoutinesInTotals = true, stretchEnabled = false)
        assertEquals(60, rules.exercisesPerDay(1))
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
