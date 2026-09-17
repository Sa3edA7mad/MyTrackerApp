package com.example.mytrackerapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleImpactTest {

    private val default = ProgramRules.DEFAULT

    /** Week 1 Day 1 fully completed under the default rules: 4 circuits x 13 exercises = 52 rows. */
    private val completedW1D1: List<Triple<Int, Int, Int>> =
        (1..4).flatMap { circuit -> (1..13).map { Triple(1, 1, circuit) } }

    @Test
    fun `no change is lossless and does not move backwards`() {
        val impact = RuleImpact.analyse(default, default, completedW1D1, emptySet())
        assertTrue(impact.isLossless)
        assertEquals(0, impact.daysReopened)
        assertEquals(0, impact.orphanedCompletions)
        assertFalse(impact.positionMovesBack)
    }

    @Test
    fun `raising exercises per circuit reopens the day and moves back`() {
        val to = default.copy(exercisesPerCircuit = 14)
        val impact = RuleImpact.analyse(default, to, completedW1D1, emptySet())
        assertEquals(1, impact.daysReopened)
        assertTrue(impact.positionMovesBack)
        assertEquals(1848, impact.newTotal)
    }

    @Test
    fun `lowering exercises per circuit does not reopen or orphan`() {
        val to = default.copy(exercisesPerCircuit = 12)
        val impact = RuleImpact.analyse(default, to, completedW1D1, emptySet())
        assertEquals(0, impact.daysReopened)
        assertEquals(0, impact.orphanedCompletions)
    }

    @Test
    fun `shrinking to one week keeps existing completions valid`() {
        val to = default.copy(weeks = 1, circuitsPerWeek = listOf(4))
        val impact = RuleImpact.analyse(default, to, completedW1D1, emptySet())
        assertEquals(6, impact.newDays)
        assertEquals(0, impact.orphanedCompletions)
    }

    @Test
    fun `shrinking week 1 circuits orphans the removed circuits without reopening`() {
        val to = default.copy(circuitsPerWeek = listOf(2, 5, 6, 7))
        val impact = RuleImpact.analyse(default, to, completedW1D1, emptySet())
        assertEquals(26, impact.orphanedCompletions)
        assertEquals(0, impact.daysReopened)
    }
}
