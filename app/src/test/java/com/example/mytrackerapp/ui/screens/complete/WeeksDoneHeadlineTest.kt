package com.example.mytrackerapp.ui.screens.complete

import org.junit.Assert.assertEquals
import org.junit.Test

class WeeksDoneHeadlineTest {

    @Test
    fun `shipped program keeps its original headline`() {
        assertEquals("Four weeks done", weeksDoneHeadline(4))
    }

    @Test
    fun `headline follows the cycle's own length`() {
        assertEquals("One week done", weeksDoneHeadline(1))
        assertEquals("Six weeks done", weeksDoneHeadline(6))
        assertEquals("26 weeks done", weeksDoneHeadline(26))
    }
}
