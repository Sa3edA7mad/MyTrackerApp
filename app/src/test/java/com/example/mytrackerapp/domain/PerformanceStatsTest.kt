package com.example.mytrackerapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PerformanceStatsTest {

    private val zone = ZoneOffset.UTC

    /** Noon UTC on [date], safely clear of any rollover-hour boundary. */
    private fun at(date: LocalDate): Long =
        date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `an empty list summarises to all nulls`() {
        val s = summarise(emptyList(), zone = zone)
        assertEquals(0, s.sets)
        assertEquals(0, s.loggedSets)
        assertNull(s.bestLoadKg)
        assertNull(s.bestReps)
        assertNull(s.bestHoldSeconds)
        assertNull(s.totalVolumeKg)
        assertNull(s.loadTrendPercent)
    }

    @Test
    fun `unlogged sets count toward sets but not loggedSets or volume`() {
        val logs = listOf(SetLog(at(LocalDate.of(2026, 1, 1)), null, null, null, null))
        val s = summarise(logs, zone = zone)
        assertEquals(1, s.sets)
        assertEquals(0, s.loggedSets)
        assertNull(s.totalVolumeKg)
    }

    @Test
    fun `volume is reps times load`() {
        val logs = listOf(SetLog(at(LocalDate.of(2026, 1, 1)), reps = 8, loadKg = 20.0, holdSeconds = null, rpe = null))
        val s = summarise(logs, zone = zone)
        assertEquals(160.0, s.totalVolumeKg!!, 1e-9)
    }

    @Test
    fun `best load is the max across sets`() {
        val logs = listOf(
            SetLog(at(LocalDate.of(2026, 1, 1)), 8, 20.0, null, null),
            SetLog(at(LocalDate.of(2026, 1, 2)), 8, 25.0, null, null),
            SetLog(at(LocalDate.of(2026, 1, 3)), 8, 22.0, null, null)
        )
        val s = summarise(logs, zone = zone)
        assertEquals(25.0, s.bestLoadKg!!, 1e-9)
    }

    @Test
    fun `a same day pair groups into one DayVolume`() {
        val day = LocalDate.of(2026, 1, 1)
        val logs = listOf(
            SetLog(at(day), 8, 20.0, null, null),
            SetLog(at(day), 8, 20.0, null, null)
        )
        val s = summarise(logs, zone = zone)
        assertEquals(1, s.volumeByDay.size)
        assertEquals(2, s.volumeByDay.first().sets)
        assertEquals(320.0, s.volumeByDay.first().volumeKg, 1e-9)
    }

    @Test
    fun `a rollover hour set lands on the previous training date`() {
        // 02:00 UTC on Jan 2nd, with a 4am rollover, belongs to Jan 1st.
        val epochMs = LocalDate.of(2026, 1, 2).atTime(2, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val logs = listOf(SetLog(epochMs, 8, 20.0, null, null))
        val s = summarise(logs, rolloverHour = 4, zone = zone)
        assertEquals(LocalDate.of(2026, 1, 1), s.volumeByDay.first().date)
    }

    @Test
    fun `trend from 20kg to 25kg reads plus 25 percent`() {
        val logs = listOf(
            SetLog(at(LocalDate.of(2026, 1, 1)), 8, 20.0, null, null),
            SetLog(at(LocalDate.of(2026, 1, 10)), 8, 25.0, null, null)
        )
        val s = summarise(logs, zone = zone)
        assertEquals(25, s.loadTrendPercent)
    }
}
