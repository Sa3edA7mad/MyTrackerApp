package com.example.mytrackerapp.ui.screens.circuit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldTimerTest {

    private var clock = 0L
    private fun timer(total: Int = 15) = HoldTimerState(total) { clock }

    @Test
    fun `starts full and unstarted`() {
        val t = timer()
        assertEquals(15_000L, t.remainingMs)
        assertEquals(15, t.secondsLeft)
        assertFalse(t.running)
        assertFalse(t.started)
        assertFalse(t.finished)
    }

    /**
     * The property that matters.
     *
     * Six seconds pass with ZERO calls to tick(), which is what happens when the app is
     * backgrounded, dozed, or interrupted by a call. A timer that decremented a counter
     * on each tick would still read 15 here. Reading the clock gives 9.
     */
    @Test
    fun `elapsed time counts even when no ticks happened`() {
        val t = timer(15)
        clock = 1_000
        t.start()

        clock = 7_000 // 6s later, never ticked

        t.tick()
        assertEquals(9_000L, t.remainingMs)
        assertEquals(9, t.secondsLeft)
        assertTrue(t.running)
    }

    @Test
    fun `pause freezes remaining and later clock movement is ignored`() {
        val t = timer(15)
        clock = 0
        t.start()

        clock = 5_000
        t.pause()
        assertEquals(10_000L, t.remainingMs)
        assertFalse(t.running)

        clock = 60_000 // a minute goes by while paused
        t.tick()
        assertEquals("a paused hold must not drain", 10_000L, t.remainingMs)
    }

    @Test
    fun `resume continues from where it was paused`() {
        val t = timer(15)
        clock = 0
        t.start()
        clock = 5_000
        t.pause() // 10s left

        clock = 100_000 // long gap while paused
        t.start()
        clock = 103_000 // 3s of actual running
        t.tick()

        assertEquals(7_000L, t.remainingMs)
    }

    @Test
    fun `clamps at zero and stops running`() {
        val t = timer(15)
        clock = 0
        t.start()

        clock = 999_999
        t.tick()

        assertEquals(0L, t.remainingMs)
        assertEquals(0, t.secondsLeft)
        assertTrue(t.finished)
        assertFalse("must not keep running past zero", t.running)
    }

    @Test
    fun `reset restores the full hold`() {
        val t = timer(30)
        clock = 0
        t.start()
        clock = 10_000
        t.tick()
        assertEquals(20_000L, t.remainingMs)

        t.reset()
        assertEquals(30_000L, t.remainingMs)
        assertFalse(t.running)
        assertFalse(t.started)
    }

    @Test
    fun `starting a finished hold runs it again from full`() {
        val t = timer(15)
        clock = 0
        t.start()
        clock = 20_000
        t.tick()
        assertTrue(t.finished)

        t.start()
        assertEquals(15_000L, t.remainingMs)
        assertTrue(t.running)

        clock = 24_000
        t.tick()
        assertEquals(11_000L, t.remainingMs)
    }

    @Test
    fun `progress drains from one to zero`() {
        val t = timer(20)
        clock = 0
        assertEquals(1f, t.progress, 0.001f)

        t.start()
        clock = 5_000
        t.tick()
        assertEquals(0.75f, t.progress, 0.001f)

        clock = 20_000
        t.tick()
        assertEquals(0f, t.progress, 0.001f)
    }

    @Test
    fun `seconds left rounds up so the last partial second still shows`() {
        val t = timer(15)
        clock = 0
        t.start()
        clock = 14_100 // 900ms left
        t.tick()
        assertEquals(1, t.secondsLeft)
        assertFalse(t.finished)
    }

    @Test
    fun `a 45 second stretch hold behaves the same`() {
        val t = timer(45)
        clock = 0
        t.start()
        clock = 44_000
        t.tick()
        assertEquals(1, t.secondsLeft)
    }
}
