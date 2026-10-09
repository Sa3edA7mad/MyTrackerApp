package com.example.mytrackerapp.data

import com.example.mytrackerapp.data.seed.LibrarySeed
import com.example.mytrackerapp.data.seed.SeedData
import com.example.mytrackerapp.data.seed.StarterPrograms
import com.example.mytrackerapp.domain.PlanValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The workbook import and the starter programs are plain data — pin their shape on the JVM. */
class LibrarySeedTest {

    @Test
    fun `the import is 123 library-slot rows with unique ids that never collide with Home's`() {
        val lib = LibrarySeed.EXERCISES
        assertEquals(123, lib.size)
        assertEquals(lib.size, lib.map { it.id }.toSet().size)
        assertTrue(lib.none { it.id in SeedData.ALL_EXERCISES.map { e -> e.id } })
        assertTrue(lib.all { it.slot == "LIBRARY" })
        assertEquals(mapOf("GYM" to 32, "CROSSFIT" to 32, "MOBILITY" to 29, "CORE" to 30), lib.groupingBy { it.category }.eachCount())
        assertTrue(lib.all { it.videoUrl.startsWith("https://www.youtube.com/watch?v=") })
        assertTrue(lib.all { it.level in setOf("BEGINNER", "INTERMEDIATE", "ADVANCED") && it.cue.isNotBlank() })
        assertTrue(lib.all { "," !in it.muscles })
    }

    @Test
    fun `starter programs only reference catalog exercises and validate`() {
        val ids = SeedData.CATALOG.map { it.id }.toSet()
        StarterPrograms.ALL.forEach { p ->
            val all = (p.plan.circuits + p.plan.warmUp + p.plan.stretch).flatMap { it.items }.map { it.exerciseId }
            assertTrue("${p.name}: ${all - ids}", ids.containsAll(all))
            assertEquals(emptyList<String>(), PlanValidation.validate(p.plan))
        }
    }
}
