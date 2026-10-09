package com.example.mytrackerapp.ui.screens.circuit

import com.example.mytrackerapp.domain.CircuitFormat
import com.example.mytrackerapp.domain.CircuitPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutClockTest {

    private fun plan(format: CircuitFormat, work: Int = 0, rest: Int = 0, rounds: Int = 0, minutes: Int = 0) =
        CircuitPlan("a", "A", format = format, workSeconds = work, restSeconds = rest, rounds = rounds, minutes = minutes)

    @Test
    fun `standard circuits have no clock`() {
        assertNull(clockState(plan(CircuitFormat.STANDARD), 10))
    }

    @Test
    fun `emom counts down each minute and ends after the last`() {
        val emom = plan(CircuitFormat.EMOM, rounds = 12)
        assertEquals(ClockState("MINUTE 1 / 12", 60, false), clockState(emom, 0))
        assertEquals(ClockState("MINUTE 3 / 12", 42, false), clockState(emom, 138))
        assertTrue(clockState(emom, 720)!!.finished)
    }

    @Test
    fun `intervals alternate work and rest`() {
        val tabata = plan(CircuitFormat.INTERVAL, work = 40, rest = 20, rounds = 8)
        assertEquals(ClockState("ROUND 1 / 8 · WORK", 40, false), clockState(tabata, 0))
        assertEquals(ClockState("ROUND 1 / 8 · REST", 15, false), clockState(tabata, 45))
        assertEquals(ClockState("ROUND 2 / 8 · WORK", 40, false), clockState(tabata, 60))
        assertTrue(clockState(tabata, 480)!!.finished)
    }

    @Test
    fun `amrap counts down and for-time counts up to its cap`() {
        assertEquals(ClockState("AMRAP 15 MIN", 840, false), clockState(plan(CircuitFormat.AMRAP, minutes = 15), 60))
        assertEquals(ClockState("FOR TIME", 95, false), clockState(plan(CircuitFormat.FOR_TIME), 95))
        assertTrue(clockState(plan(CircuitFormat.FOR_TIME, minutes = 10), 600)!!.finished)
        assertEquals("12:05", formatClock(725))
    }
}
