package com.example.mytrackerapp.e2e

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** THM-xx in docs/FEATURES.md: the three glass-gray themes and the Appearance setting. */
@RunWith(AndroidJUnit4::class)
class ThemeE2eTest : E2eTest() {

    @After
    fun restoreDeviceMode() {
        shell("cmd uimode night auto")
    }

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command).use { fd ->
                java.io.FileInputStream(fd.fileDescriptor).readBytes()
            }
    }

    /**
     * Median linear luminance of the screen on a coarse grid. The median ignores text, icons
     * and the lime/green button, so it reads the background and card color of the theme.
     */
    private fun screenLuminance(): Float {
        val map = rule.onRoot().captureToImage().toPixelMap()
        val steps = 24
        val samples = ArrayList<Float>(steps * steps)
        for (i in 0 until steps) for (j in 0 until steps) {
            val x = (i * map.width / steps) + map.width / (2 * steps)
            val y = (j * map.height / steps) + map.height / (2 * steps)
            samples += map[x, y].luminance()
        }
        return samples.sorted()[samples.size / 2]
    }

    private fun waitForScreen(timeoutMs: Long = 10_000, check: (Float) -> Boolean) {
        rule.waitUntil(timeoutMs) { check(screenLuminance()) }
    }

    private fun pickTheme(label: String) {
        tapDesc("Settings")
        tapText(label)
    }

    @Test
    fun thm01_eachManualThemeRepaintsTheApp() {
        shell("cmd uimode night no")
        pickTheme("Light charcoal")
        waitForScreen { it < 0.15f }
        tapText("Steel glass")
        waitForScreen { it in 0.18f..0.5f }
        tapText("Pale frost")
        waitForScreen { it > 0.5f }
    }

    @Test
    fun thm02_matchDeviceFollowsTheDeviceMode() {
        shell("cmd uimode night yes")
        waitForScreen { it < 0.15f }
        shell("cmd uimode night no")
        waitForScreen { it > 0.5f }
    }

    @Test
    fun thm03_aManualThemeIgnoresTheDeviceMode() {
        shell("cmd uimode night yes")
        waitForScreen { it < 0.15f }
        pickTheme("Pale frost")
        waitForScreen { it > 0.5f }
        shell("cmd uimode night no")
        rule.waitForIdle()
        waitForScreen { it > 0.5f }
        shell("cmd uimode night yes")
        rule.waitForIdle()
        waitForScreen { it > 0.5f }
    }

    @Test
    fun thm04_theChosenThemeSurvivesARelaunch() {
        shell("cmd uimode night no")
        pickTheme("Light charcoal")
        waitForScreen { it < 0.15f }
        relaunch()
        waitForScreen { it < 0.15f }
    }
}
