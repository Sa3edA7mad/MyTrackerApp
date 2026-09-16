package com.example.mytrackerapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RecentTalliesTest {

    private val utc = ZoneId.of("UTC")

    private fun at(y: Int, mo: Int, d: Int, h: Int = 12): Long =
        LocalDateTime.of(y, mo, d, h, 0).atZone(utc).toInstant().toEpochMilli()

    @Test
    fun `no completions means no bars`() {
        assertTrue(recentTallies(emptyList(), zone = utc).isEmpty())
    }

    @Test
    fun `counts are grouped per training date and sorted ascending`() {
        val times = listOf(
            at(2026, 9, 14), at(2026, 9, 14),
            at(2026, 9, 15),
            at(2026, 9, 16), at(2026, 9, 16), at(2026, 9, 16)
        )
        val tallies = recentTallies(times, zone = utc)

        assertEquals(3, tallies.size)
        assertEquals(LocalDate.of(2026, 9, 14), tallies[0].date)
        assertEquals(2, tallies[0].count)
        assertEquals(1, tallies[1].count)
        assertEquals(3, tallies[2].count)
    }

    @Test
    fun `only the most recent seven dates are kept`() {
        val times = (1..10).map { at(2026, 9, it) }
        val tallies = recentTallies(times, zone = utc)

        assertEquals(7, tallies.size)
        assertEquals(LocalDate.of(2026, 9, 4), tallies.first().date)
        assertEquals(LocalDate.of(2026, 9, 10), tallies.last().date)
    }

    @Test
    fun `the limit is configurable`() {
        val times = (1..10).map { at(2026, 9, it) }
        assertEquals(3, recentTallies(times, limit = 3, zone = utc).size)
    }

    @Test
    fun `the 4am rollover groups a late set with the previous day`() {
        // 01:00 on the 16th belongs to the 15th, so both land in one bucket.
        val times = listOf(at(2026, 9, 15, h = 22), at(2026, 9, 16, h = 1))
        val tallies = recentTallies(times, zone = utc)

        assertEquals(1, tallies.size)
        assertEquals(LocalDate.of(2026, 9, 15), tallies.single().date)
        assertEquals(2, tallies.single().count)
    }

    @Test
    fun `gaps do not produce empty bars`() {
        // Trained on the 1st and the 20th only — two bars, not twenty.
        val times = listOf(at(2026, 9, 1), at(2026, 9, 20))
        assertEquals(2, recentTallies(times, zone = utc).size)
    }
}
