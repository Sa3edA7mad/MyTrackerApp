package com.example.mytrackerapp.domain

import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.ExerciseSlot
import com.example.mytrackerapp.domain.model.TargetType
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogValidationTest {

    private fun draft(
        name: String = "Squat",
        targetValue: Int = 5,
        videoUrl: String = "",
        progressionStep: Int = 0
    ) = ExerciseDraft(
        name = name,
        category = Category.BODYWEIGHT,
        slot = ExerciseSlot.PROGRAM,
        muscles = "Legs",
        instructions = "Squat down.",
        targetType = TargetType.REPS,
        targetValue = targetValue,
        perSide = false,
        targetLabel = "5 reps",
        videoUrl = videoUrl,
        progressionStep = progressionStep
    )

    @Test
    fun `a well formed draft is valid`() {
        assertTrue(CatalogValidation.validate(draft()).isEmpty())
    }

    @Test
    fun `blank name is invalid`() {
        assertTrue(CatalogValidation.validate(draft(name = "")).isNotEmpty())
    }

    @Test
    fun `name over the length limit is invalid`() {
        assertTrue(CatalogValidation.validate(draft(name = "x".repeat(61))).isNotEmpty())
    }

    @Test
    fun `target value out of range is invalid`() {
        assertTrue(CatalogValidation.validate(draft(targetValue = 0)).isNotEmpty())
        assertTrue(CatalogValidation.validate(draft(targetValue = 1000)).isNotEmpty())
    }

    @Test
    fun `a blank video url is fine`() {
        assertTrue(CatalogValidation.validate(draft(videoUrl = "")).isEmpty())
    }

    @Test
    fun `a video url without a scheme is invalid`() {
        assertTrue(CatalogValidation.validate(draft(videoUrl = "www.example.com")).isNotEmpty())
    }

    @Test
    fun `an https video url is fine`() {
        assertTrue(CatalogValidation.validate(draft(videoUrl = "https://example.com")).isEmpty())
    }

    @Test
    fun `progression step out of range is invalid`() {
        assertTrue(CatalogValidation.validate(draft(progressionStep = 51)).isNotEmpty())
        assertTrue(CatalogValidation.validate(draft(progressionStep = -1)).isNotEmpty())
    }
}
