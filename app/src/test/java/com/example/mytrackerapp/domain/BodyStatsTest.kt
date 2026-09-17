package com.example.mytrackerapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyStatsTest {

    @Test
    fun `bmi of 80kg at 180cm is about 24_7`() {
        assertEquals(24.7, bmi(80.0, 180.0)!!, 0.05)
    }

    @Test
    fun `bmi is null when an input is missing`() {
        assertNull(bmi(null, 180.0))
        assertNull(bmi(80.0, null))
    }

    @Test
    fun `bmi is null rather than dividing by zero`() {
        assertNull(bmi(80.0, 0.0))
    }

    @Test
    fun `waist to hip is a simple ratio`() {
        assertEquals(0.85, waistToHip(85.0, 100.0)!!, 1e-9)
    }

    @Test
    fun `waist to hip is null without both inputs`() {
        assertNull(waistToHip(85.0, null))
    }

    @Test
    fun `lean mass subtracts body fat percent`() {
        assertEquals(64.0, leanMassKg(80.0, 20.0)!!, 1e-9)
    }

    @Test
    fun `a single point has null deltas`() {
        val trend = trendFor("bodyweight", listOf(MetricPoint(1000L, 80.0)))
        assertEquals(80.0, trend.latest)
        assertNull(trend.previous)
        assertNull(trend.deltaFromPrevious)
        assertNull(trend.deltaFromFirst)
    }

    @Test
    fun `three ascending points report the right deltas in order`() {
        val points = listOf(
            MetricPoint(3000L, 82.0),
            MetricPoint(1000L, 80.0),
            MetricPoint(2000L, 81.0)
        )
        val trend = trendFor("bodyweight", points)
        assertEquals(listOf(1000L, 2000L, 3000L), trend.points.map { it.takenAt })
        assertEquals(82.0, trend.latest)
        assertEquals(81.0, trend.previous)
        assertEquals(80.0, trend.first)
        assertEquals(1.0, trend.deltaFromPrevious!!, 1e-9)
        assertEquals(2.0, trend.deltaFromFirst!!, 1e-9)
    }
}
