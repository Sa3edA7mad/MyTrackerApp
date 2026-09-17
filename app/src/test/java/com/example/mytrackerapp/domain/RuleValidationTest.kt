package com.example.mytrackerapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleValidationTest {

    @Test
    fun `default rules are valid`() {
        assertTrue(RuleValidation.validate(ProgramRules.DEFAULT).isEmpty())
    }

    @Test
    fun `zero weeks is invalid`() {
        assertEquals(
            1,
            RuleValidation.validate(
                ProgramRules.DEFAULT.copy(weeks = 0, circuitsPerWeek = emptyList())
            ).size
        )
    }

    @Test
    fun `too many weeks is invalid`() {
        assertEquals(
            1,
            RuleValidation.validate(
                ProgramRules.DEFAULT.copy(
                    weeks = 27,
                    circuitsPerWeek = List(27) { 4 }
                )
            ).size
        )
    }

    @Test
    fun `too many days per week is invalid`() {
        assertEquals(1, RuleValidation.validate(ProgramRules.DEFAULT.copy(daysPerWeek = 8)).size)
    }

    @Test
    fun `rollover hour out of range is invalid`() {
        assertEquals(1, RuleValidation.validate(ProgramRules.DEFAULT.copy(dayRolloverHour = 24)).size)
    }

    @Test
    fun `zero exercises per circuit is invalid`() {
        assertEquals(
            1,
            RuleValidation.validate(ProgramRules.DEFAULT.copy(exercisesPerCircuit = 0)).size
        )
    }

    @Test
    fun `mismatched circuits list size is invalid`() {
        val errors = RuleValidation.validate(ProgramRules.DEFAULT.copy(weeks = 3))
        assertTrue(errors.any { it.contains("circuit counts") })
    }

    @Test
    fun `counting routines with both disabled is invalid`() {
        val errors = RuleValidation.validate(
            ProgramRules.DEFAULT.copy(
                countRoutinesInTotals = true,
                warmUpEnabled = false,
                stretchEnabled = false
            )
        )
        assertTrue(errors.isNotEmpty())
    }
}
