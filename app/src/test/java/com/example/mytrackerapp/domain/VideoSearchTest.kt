package com.example.mytrackerapp.domain

import com.example.mytrackerapp.data.seed.SeedData
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.TargetType
import com.example.mytrackerapp.domain.model.formVideoUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSearchTest {

    private val base = "https://www.youtube.com/results?search_query="

    private fun ex(name: String, videoUrl: String) = Exercise(
        id = "x",
        name = name,
        category = Category.BODYWEIGHT,
        muscles = "",
        instructions = "",
        targetType = TargetType.REPS,
        targetValue = 5,
        perSide = false,
        targetLabel = "5 reps",
        videoUrl = videoUrl,
        sortOrder = 1
    )

    @Test
    fun `searches for the name alone`() {
        assertEquals("${base}Goblet%20Squat", youtubeSearchUrl("Goblet Squat"))
        assertFalse(youtubeSearchUrl("Goblet Squat").contains("proper"))
    }

    @Test
    fun `encodes punctuation the same way the seed links do`() {
        assertEquals("${base}Child%27s%20Pose", youtubeSearchUrl("Child's Pose"))
        assertEquals("${base}Chest%20Opener%20%28Band%29", youtubeSearchUrl("Chest Opener (Band)"))
        assertEquals("${base}Cat-Cow", youtubeSearchUrl("Cat-Cow"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("${base}Squat", youtubeSearchUrl("  Squat "))
    }

    @Test
    fun `a blank video url falls back to a name search`() {
        assertEquals("${base}Goblet%20Squat", ex("Goblet Squat", "").formVideoUrl)
        assertEquals("${base}Goblet%20Squat", ex("Goblet Squat", "   ").formVideoUrl)
    }

    @Test
    fun `an entered video url is used as is`() {
        val url = "https://www.youtube.com/watch?v=WDIpL0pjun0"
        assertEquals(url, ex("Push-up", url).formVideoUrl)
    }

    @Test
    fun `every seeded search link matches the generated one`() {
        val searches = SeedData.ALL_EXERCISES.filter { it.videoUrl.contains("search_query=") }
        // 29 minus the 4 original real videos, minus Cat-Cow and Child's Pose (library videos merged in).
        assertEquals(23, searches.size)
        searches.forEach { assertEquals(it.name, youtubeSearchUrl(it.name), it.videoUrl) }
        assertTrue(SeedData.ALL_EXERCISES.none { it.videoUrl.contains("proper") })
    }
}
