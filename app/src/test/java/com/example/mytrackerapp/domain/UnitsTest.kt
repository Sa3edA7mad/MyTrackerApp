package com.example.mytrackerapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test
    fun `kg to lb round trips`() {
        val kgValues = listOf(0.0, 1.0, 20.5, 100.0, 250.3)
        kgValues.forEach { kg ->
            val lb = Units.kgToDisplay(kg, WeightUnit.LB)
            val backToKg = Units.displayToKg(lb, WeightUnit.LB)
            assertEquals(kg, backToKg, 1e-6)
        }
    }

    @Test
    fun `100 kg is about 220_46 lb`() {
        assertEquals(220.462262, Units.kgToDisplay(100.0, WeightUnit.LB), 1e-4)
    }

    @Test
    fun `cm to in round trips`() {
        assertEquals(1.0, Units.cmToDisplay(2.54, LengthUnit.IN), 1e-9)
    }

    @Test
    fun `kg unit is a no-op`() {
        assertEquals(42.0, Units.kgToDisplay(42.0, WeightUnit.KG), 0.0)
        assertEquals(42.0, Units.displayToKg(42.0, WeightUnit.KG), 0.0)
    }

    @Test
    fun `cm unit is a no-op`() {
        assertEquals(42.0, Units.cmToDisplay(42.0, LengthUnit.CM), 0.0)
        assertEquals(42.0, Units.displayToCm(42.0, LengthUnit.CM), 0.0)
    }

    @Test
    fun `format respects decimals`() {
        assertEquals("20.5", Units.format(20.5, 1))
        assertEquals("21", Units.format(20.5, 0))
    }
}
