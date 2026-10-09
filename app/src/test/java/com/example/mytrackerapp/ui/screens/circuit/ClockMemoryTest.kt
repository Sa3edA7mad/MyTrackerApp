package com.example.mytrackerapp.ui.screens.circuit

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockMemoryTest {

    private val hour = 60 * 60 * 1000L

    @Test
    fun `a clock started earlier is still running when you come back`() {
        ClockMemory.setStart("p1/1/1/1", 5_000L)
        assertEquals(5_000L, ClockMemory.start("p1/1/1/1", nowMs = 65_000L))
    }

    @Test
    fun `each slot has its own clock and reset forgets it`() {
        ClockMemory.setStart("p1/1/1/2", 5_000L)
        assertEquals(0L, ClockMemory.start("p1/1/1/3", nowMs = 9_000L))
        ClockMemory.setStart("p1/1/1/2", 0L)
        assertEquals(0L, ClockMemory.start("p1/1/1/2", nowMs = 9_000L))
    }

    @Test
    fun `a start older than six hours or from before a reboot reads as not started`() {
        ClockMemory.setStart("old", 1_000L)
        assertEquals(0L, ClockMemory.start("old", nowMs = 7 * hour))
        ClockMemory.setStart("future", 50_000L)
        assertEquals(0L, ClockMemory.start("future", nowMs = 10_000L))
    }

    @Test
    fun `rounds are remembered per slot`() {
        ClockMemory.setRounds("r", 3)
        assertEquals(3, ClockMemory.rounds("r"))
        assertEquals(0, ClockMemory.rounds("other"))
    }
}
