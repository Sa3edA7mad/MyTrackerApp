package com.example.mytrackerapp.domain.model

import com.example.mytrackerapp.domain.DAYS_PER_WEEK
import com.example.mytrackerapp.domain.EXERCISES_PER_CIRCUIT
import com.example.mytrackerapp.domain.WEEKS
import com.example.mytrackerapp.domain.circuitsForWeek
import com.example.mytrackerapp.domain.exercisesPerDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The derived totals the Program and Progress screens render.
 *
 * These are the numbers the plan commits to — 24/30/36/42 circuits, 132 total, and 18%
 * after a complete week one — so an off-by-one here is a visible, wrong number on screen.
 */
class ProgramTotalsTest {

    private fun day(week: Int, day: Int, done: Int, closed: Boolean = false) = DaySummary(
        week = week,
        day = day,
        done = done,
        total = exercisesPerDay(week),
        closed = closed
    )

    private fun fullWeek(week: Int) = WeekState(
        week = week,
        circuitsPerDay = circuitsForWeek(week),
        days = (1..DAYS_PER_WEEK).map { day(week, it, exercisesPerDay(week)) },
        isCurrent = false
    )

    @Test
    fun `circuits per week read 24 30 36 42 and sum to 132`() {
        val totals = (1..WEEKS).map { fullWeek(it).circuitsTotal }
        assertEquals(listOf(24, 30, 36, 42), totals)
        assertEquals(132, totals.sum())
    }

    @Test
    fun `a fully complete week counts every one of its circuits`() {
        (1..WEEKS).forEach { week ->
            val w = fullWeek(week)
            assertEquals(w.circuitsTotal, w.circuitsDone)
            assertEquals(w.exercisesTotal, w.exercisesDone)
        }
    }

    @Test
    fun `exercises per week read 312 390 468 546 and sum to 1716`() {
        val totals = (1..WEEKS).map { fullWeek(it).exercisesTotal }
        assertEquals(listOf(312, 390, 468, 546), totals)
        assertEquals(1716, totals.sum())
    }

    @Test
    fun `a partly done day contributes only its whole circuits`() {
        // 26 exercises in week 1 is exactly two circuits.
        val w = WeekState(
            week = 1,
            circuitsPerDay = 4,
            days = listOf(day(1, 1, 26)) + (2..6).map { day(1, it, 0) },
            isCurrent = true
        )
        assertEquals(2, w.circuitsDone)
        assertEquals(26, w.exercisesDone)

        // 25 is one circuit plus change, so still only one whole circuit.
        val partial = WeekState(1, 4, listOf(day(1, 1, 25)), isCurrent = true)
        assertEquals(1, partial.circuitsDone)
    }

    @Test
    fun `day states are mutually consistent`() {
        val untouched = day(1, 1, 0)
        assertFalse(untouched.isComplete)
        assertFalse(untouched.isPartial)
        assertTrue(untouched.isUntouched)

        val partial = day(1, 1, 20)
        assertFalse(partial.isComplete)
        assertTrue(partial.isPartial)
        assertFalse(partial.isUntouched)

        val complete = day(1, 1, 52)
        assertTrue(complete.isComplete)
        assertFalse(complete.isPartial)

        // A day closed early with no work is not "untouched" — it was a decision.
        val closedEmpty = day(1, 1, 0, closed = true)
        assertFalse(closedEmpty.isUntouched)
        assertFalse(closedEmpty.isComplete)
    }

    @Test
    fun `cycle percent after a complete week one reads 18`() {
        val stats = stats(exercisesDone = 312)
        assertEquals(18, stats.percent)
    }

    @Test
    fun `cycle percent after a single week one day reads 3`() {
        assertEquals(3, stats(exercisesDone = 52).percent)
    }

    @Test
    fun `cycle percent is 0 at the start and 100 at the end`() {
        assertEquals(0, stats(exercisesDone = 0).percent)
        assertEquals(100, stats(exercisesDone = 1716).percent)
    }

    @Test
    fun `percent does not divide by zero on an empty cycle`() {
        assertEquals(0, stats(exercisesDone = 0, exercisesTotal = 0).percent)
    }

    private fun stats(exercisesDone: Int, exercisesTotal: Int = 1716) = CycleStats(
        streak = 0,
        exercisesDone = exercisesDone,
        exercisesTotal = exercisesTotal,
        circuitsDone = exercisesDone / EXERCISES_PER_CIRCUIT,
        circuitsTotal = 132,
        daysTrained = 0,
        weeks = emptyList(),
        heat = emptyList(),
        mostDone = emptyList()
    )
}
