package com.example.mytrackerapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ProgramTest {

    private val utc = ZoneId.of("UTC")

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(utc).toInstant().toEpochMilli()

    // --- shape of the program, straight from the workbook ---

    @Test
    fun `circuits ramp 4 to 7 across the four weeks`() {
        assertEquals(listOf(4, 5, 6, 7), (1..WEEKS).map(::circuitsForWeek))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `week 0 is rejected`() {
        circuitsForWeek(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `week 5 is rejected`() {
        circuitsForWeek(5)
    }

    @Test
    fun `exercises per day`() {
        assertEquals(listOf(52, 65, 78, 91), (1..WEEKS).map(::exercisesPerDay))
    }

    @Test
    fun `exercises per week`() {
        assertEquals(listOf(312, 390, 468, 546), (1..WEEKS).map(::exercisesForWeek))
    }

    @Test
    fun `cycle totals match the spreadsheet`() {
        assertEquals(1716, totalExercisesInCycle())
        assertEquals(132, totalCircuitsInCycle())
        assertEquals(24, ALL_POSITIONS.size)
    }

    @Test
    fun `positions are in program order`() {
        assertEquals(Position(1, 1), ALL_POSITIONS.first())
        assertEquals(Position(4, 6), ALL_POSITIONS.last())
        assertEquals(Position(2, 1), ALL_POSITIONS[6])
    }

    // --- day settlement and the current position ---

    @Test
    fun `empty cycle starts at week 1 day 1`() {
        assertEquals(Position(1, 1), nextPosition(emptyMap(), emptySet()))
    }

    @Test
    fun `a fully complete cycle has no next position`() {
        val done = ALL_POSITIONS.associateWith { exercisesPerDay(it.week) }
        assertNull(nextPosition(done, emptySet()))
    }

    @Test
    fun `a partly done day is not settled`() {
        val done = mapOf(Position(1, 1) to 51) // one short of 52
        assertEquals(Position(1, 1), nextPosition(done, emptySet()))
    }

    @Test
    fun `closing a day early releases the counter`() {
        // INVARIANT 3: without this escape, an abandoned day traps the cycle forever.
        val done = mapOf(Position(1, 1) to 10)
        val closed = setOf(Position(1, 1))
        assertEquals(Position(1, 2), nextPosition(done, closed))
    }

    @Test
    fun `a cycle finished entirely by closing days still completes`() {
        assertNull(nextPosition(emptyMap(), ALL_POSITIONS.toSet()))
    }

    @Test
    fun `settlement respects the per-week day size`() {
        // 52 finishes a week-1 day but not a week-4 day, which needs 91.
        assertEquals(true, isDaySettled(week = 1, doneCount = 52, closed = false))
        assertEquals(false, isDaySettled(week = 4, doneCount = 52, closed = false))
    }

    // --- training date and the 4am rollover ---

    @Test
    fun `a set at 0130 belongs to the previous day`() {
        assertEquals(LocalDate.of(2026, 9, 15), trainingDate(ms(2026, 9, 16, 1, 30), utc))
    }

    @Test
    fun `a set at 0500 belongs to the same day`() {
        assertEquals(LocalDate.of(2026, 9, 16), trainingDate(ms(2026, 9, 16, 5, 0), utc))
    }

    @Test
    fun `rollover happens exactly at 0400`() {
        assertEquals(LocalDate.of(2026, 9, 15), trainingDate(ms(2026, 9, 16, 3, 59), utc))
        assertEquals(LocalDate.of(2026, 9, 16), trainingDate(ms(2026, 9, 16, 4, 0), utc))
    }

    // --- streaks ---

    @Test
    fun `no training means no streak`() {
        assertEquals(0, streakDays(emptySet(), LocalDate.of(2026, 9, 16)))
    }

    @Test
    fun `three consecutive days ending today`() {
        val today = LocalDate.of(2026, 9, 16)
        val dates = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, streakDays(dates, today))
    }

    @Test
    fun `a streak ending yesterday still counts`() {
        val today = LocalDate.of(2026, 9, 16)
        val dates = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, streakDays(dates, today))
    }

    @Test
    fun `a streak broken two days ago reads zero not stale`() {
        val today = LocalDate.of(2026, 9, 16)
        val dates = setOf(today.minusDays(2), today.minusDays(3), today.minusDays(4))
        assertEquals(0, streakDays(dates, today))
    }

    @Test
    fun `a gap stops the count`() {
        val today = LocalDate.of(2026, 9, 16)
        val dates = setOf(today, today.minusDays(1), today.minusDays(3))
        assertEquals(2, streakDays(dates, today))
    }

    @Test
    fun `streaks cross a month boundary`() {
        val today = LocalDate.of(2026, 3, 1)
        val dates = setOf(today, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 2, 27))
        assertEquals(3, streakDays(dates, today))
    }

    // --- longest streak, for the end-of-cycle summary ---

    @Test
    fun `longest streak of nothing is zero`() {
        assertEquals(0, longestStreak(emptySet()))
    }

    @Test
    fun `a single training day is a streak of one`() {
        assertEquals(1, longestStreak(setOf(LocalDate.of(2026, 9, 16))))
    }

    @Test
    fun `longest streak finds the best run not the last one`() {
        val d = LocalDate.of(2026, 9, 1)
        val dates = setOf(
            d, d.plusDays(1), d.plusDays(2), d.plusDays(3), // run of 4
            d.plusDays(10),                                  // gap
            d.plusDays(20), d.plusDays(21)                   // run of 2 at the end
        )
        assertEquals(4, longestStreak(dates))
    }

    @Test
    fun `longest streak does not depend on input order`() {
        val d = LocalDate.of(2026, 9, 10)
        val shuffled = setOf(d.plusDays(2), d, d.plusDays(1), d.plusDays(5))
        assertEquals(3, longestStreak(shuffled))
    }

    @Test
    fun `longest streak crosses a month boundary`() {
        val dates = setOf(
            LocalDate.of(2026, 2, 27),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 2)
        )
        assertEquals(4, longestStreak(dates))
    }

    @Test
    fun `longest streak can exceed the live streak`() {
        // Big run early, then a gap, then today alone. Live streak is 1, best is 5.
        val today = LocalDate.of(2026, 9, 30)
        val early = LocalDate.of(2026, 9, 1)
        val dates = (0..4).map { early.plusDays(it.toLong()) }.toSet() + today
        assertEquals(1, streakDays(dates, today))
        assertEquals(5, longestStreak(dates))
    }
}
