package com.example.mytrackerapp.ui.screens.library

import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTest {

    private fun ex(id: String, name: String, muscles: String, cat: Category = Category.BAND) =
        Exercise(
            id = id,
            name = name,
            category = cat,
            muscles = muscles,
            instructions = "",
            targetType = TargetType.REPS,
            targetValue = 10,
            perSide = false,
            targetLabel = "10 reps",
            videoUrl = "",
            sortOrder = 1
        )

    private val catalog = listOf(
        ex("squat", "Squat", "Legs · Glutes · Core", Category.BODYWEIGHT),
        ex("dead_hang", "Dead Hang", "Shoulders · Spine · Grip", Category.BODYWEIGHT),
        ex("external_rotation", "External Rotation", "Rotator Cuff"),
        ex("internal_rotation", "Internal Rotation", "Rotator Cuff"),
        ex("band_row", "Band Row", "Back · Biceps · Rear Delts"),
        ex("childs_pose", "Child's Pose", "Lower Back · Hips", Category.STRETCH)
    )

    @Test
    fun `an empty query returns everything`() {
        assertEquals(catalog, filterCatalog(catalog, ""))
        assertEquals(catalog, filterCatalog(catalog, "   "))
    }

    @Test
    fun `partial name match is case insensitive`() {
        val hits = filterCatalog(catalog, "rot").map { it.id }
        assertEquals(listOf("external_rotation", "internal_rotation"), hits)
        assertEquals(hits, filterCatalog(catalog, "ROT").map { it.id })
    }

    @Test
    fun `muscles are searchable too`() {
        val hits = filterCatalog(catalog, "rotator cuff").map { it.id }
        assertEquals(listOf("external_rotation", "internal_rotation"), hits)
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(listOf("band_row"), filterCatalog(catalog, "  band row  ").map { it.id })
    }

    @Test
    fun `no match returns empty so the screen can show its empty state`() {
        assertTrue(filterCatalog(catalog, "zzz").isEmpty())
    }

    @Test
    fun `an apostrophe in a name is searchable`() {
        assertEquals(listOf("childs_pose"), filterCatalog(catalog, "child's").map { it.id })
    }
}
