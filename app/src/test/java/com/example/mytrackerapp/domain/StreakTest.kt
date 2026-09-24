package com.example.mytrackerapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StreakTest {

    private val today = LocalDate.of(2026, 9, 25)
    private fun days(vararg ago: Long) = ago.map { today.minusDays(it) }.toSet()

    @Test
    fun `no training is a zero streak`() {
        assertEquals(0, streakDays(emptySet(), today))
        assertEquals(0, longestStreak(emptySet()))
    }

    @Test
    fun `a run ending today counts every consecutive day`() {
        assertEquals(3, streakDays(days(0, 1, 2), today))
    }

    @Test
    fun `a run ending yesterday is still alive`() {
        assertEquals(2, streakDays(days(1, 2), today))
    }

    @Test
    fun `a gap of a whole day breaks the streak`() {
        assertEquals(0, streakDays(days(2, 3, 4), today))
    }

    @Test
    fun `longest streak is the best run anywhere, not the live one`() {
        assertEquals(4, longestStreak(days(0, 5, 6, 7, 8, 10)))
    }

    @Test
    fun `a set before the rollover hour belongs to the previous day`() {
        val at0100 = today.atTime(1, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val at0500 = today.atTime(5, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(today.minusDays(1), trainingDate(at0100, 4, ZoneOffset.UTC))
        assertEquals(today, trainingDate(at0500, 4, ZoneOffset.UTC))
    }
}
