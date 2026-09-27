package com.example.mytrackerapp.ui.screens.measure

import org.junit.Assert.assertEquals
import org.junit.Test

class WithUnitTest {

    @Test
    fun `unit is appended in parentheses`() {
        assertEquals("Waist (cm)", withUnit("Waist", "cm"))
    }

    @Test
    fun `a unitless metric gets no empty parentheses`() {
        assertEquals("Resting heart rate", withUnit("Resting heart rate", ""))
    }
}
