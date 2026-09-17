package com.example.mytrackerapp.ui.screens.circuit

import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the guided pager's forward navigation.
 *
 * Regression: advancing used to be a plain `index + 1`, so after ticking exercises in
 * checklist mode and switching back to guided, the pager walked you through work that was
 * already done.
 */
class NextUndoneIndexTest {

    private fun ex(id: String, perSide: Boolean = false) = Exercise(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        category = Category.BODYWEIGHT,
        muscles = "",
        instructions = "",
        targetType = TargetType.REPS,
        targetValue = 5,
        perSide = perSide,
        targetLabel = "5 reps",
        videoUrl = "",
        sortOrder = 1
    )

    private val list = listOf(ex("a"), ex("b"), ex("c"), ex("d"), ex("e"))

    @Test
    fun `advances to the very next exercise when nothing is done`() {
        assertEquals(1, nextUndoneIndex(list, emptySet(), from = 0))
        assertEquals(3, nextUndoneIndex(list, emptySet(), from = 2))
    }

    @Test
    fun `skips a single already-ticked exercise`() {
        assertEquals(2, nextUndoneIndex(list, setOf("b"), from = 0))
    }

    @Test
    fun `skips a run of already-ticked exercises`() {
        assertEquals(4, nextUndoneIndex(list, setOf("b", "c", "d"), from = 0))
    }

    @Test
    fun `returns null when everything after the current one is done`() {
        assertNull(nextUndoneIndex(list, setOf("b", "c", "d", "e"), from = 0))
    }

    @Test
    fun `returns null at the end of the list`() {
        assertNull(nextUndoneIndex(list, emptySet(), from = 4))
    }

    @Test
    fun `only looks forward so the current exercise's own state is irrelevant`() {
        // The caller writes the completion for `from` asynchronously immediately before
        // calling this, so `from` may or may not be in doneIds. Either way: same answer.
        assertEquals(1, nextUndoneIndex(list, setOf("a"), from = 0))
        assertEquals(1, nextUndoneIndex(list, emptySet(), from = 0))
    }

    @Test
    fun `a done exercise earlier in the list does not affect forward movement`() {
        assertEquals(4, nextUndoneIndex(list, setOf("a", "b", "d"), from = 2))
    }

    @Test
    fun `an empty circuit has nowhere to go`() {
        assertNull(nextUndoneIndex(emptyList(), emptySet(), from = 0))
    }

    @Test
    fun `a fully complete circuit exits rather than looping`() {
        val allDone = list.map { it.id }.toSet()
        assertNull(nextUndoneIndex(list, allDone, from = 0))
    }

    @Test
    fun `the real 13-exercise shape skips to the right band exercise`() {
        // Bodyweight 1-6 ticked in checklist, resume guided from index 0.
        val circuit = listOf(
            ex("squat"), ex("push_up"), ex("dead_hang"), ex("crunch"),
            ex("superman"), ex("glute_bridge"), ex("bicep_curl"),
            ex("tricep_pushdown"), ex("pull_apart"), ex("external_rotation", perSide = true),
            ex("internal_rotation", perSide = true), ex("shoulder_press"), ex("band_row")
        )
        val done = setOf(
            "push_up", "dead_hang", "crunch", "superman", "glute_bridge"
        )
        // Finishing squat (0) should jump straight past the ticked block to bicep_curl (6).
        assertEquals(6, nextUndoneIndex(circuit, done, from = 0))
    }
}
