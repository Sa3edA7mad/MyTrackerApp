package com.example.mytrackerapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TargetForWeekTest {

    private fun exercise(targetValue: Int, progressionStep: Int) = Exercise(
        id = "x",
        name = "X",
        category = Category.BODYWEIGHT,
        muscles = "",
        instructions = "",
        targetType = TargetType.REPS,
        targetValue = targetValue,
        perSide = false,
        targetLabel = "",
        videoUrl = "",
        sortOrder = 1,
        progressionStep = progressionStep
    )

    @Test
    fun `zero progression step is a constant target`() {
        val e = exercise(targetValue = 5, progressionStep = 0)
        assertEquals(listOf(5, 5, 5, 5), (1..4).map(e::targetForWeek))
    }

    @Test
    fun `progression step of one adds one exercise per week`() {
        val e = exercise(targetValue = 5, progressionStep = 1)
        assertEquals(listOf(5, 6, 7, 8), (1..4).map(e::targetForWeek))
        assertEquals(8, e.targetForWeek(4))
    }
}
