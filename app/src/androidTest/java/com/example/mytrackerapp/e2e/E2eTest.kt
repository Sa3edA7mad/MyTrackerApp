package com.example.mytrackerapp.e2e

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.example.mytrackerapp.MainActivity
import com.example.mytrackerapp.TrackerApplication
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.data.prefs.ThemeMode
import com.example.mytrackerapp.di.AppContainer
import com.example.mytrackerapp.domain.ExerciseDraft
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.model.Exercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * Base for the end-to-end UI suite: the real [MainActivity], the real on-device database
 * and the real DataStore — no fakes.
 *
 * Every test starts from a fresh install state: tables are cleared and re-seeded exactly as
 * on first launch, and every setting is put back to its default. [arrange] then runs
 * against that state *before* the activity starts, so a test can begin mid-program without
 * tapping through hundreds of checkboxes. Everything a test asserts is read off the screen.
 *
 * Test names carry the regression IDs from docs/FEATURES.md (e.g. `tod03_…` is TOD-03).
 */
abstract class E2eTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    protected val container: AppContainer
        get() = (ApplicationProvider.getApplicationContext<Context>() as TrackerApplication).container

    /** Runs on a freshly seeded database, before the activity launches. */
    protected open suspend fun arrange() {}

    @Before
    fun resetAndLaunch() {
        runBlocking {
            resetToFreshInstall()
            arrange()
        }
        launch()
    }

    @After
    fun closeActivity() {
        scenario?.close()
    }

    protected fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("TODAY")
    }

    /** Kills and restarts the activity, to prove something survives a relaunch. */
    protected fun relaunch() {
        scenario?.close()
        launch()
    }

    private suspend fun resetToFreshInstall() {
        val db = container.db
        db.clearAllTables()
        // One transaction pins the seed to one pooled connection — SeedCallback reads
        // last_insert_rowid(), which is per-connection.
        val sql = db.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            SeedCallback.onCreate(sql)
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        with(container.settings) {
            setGuidedMode(true)
            setHaptics(true)
            setSoundCues(false) // defaults to on; off keeps the emulator quiet, not asserted
            setKeepScreenOn(true)
            setAutoAdvanceTimer(true)
            setThemeMode(ThemeMode.SYSTEM)
            setTodayProgramId(null)
        }
    }

    /* ------------------------------------------------------------ arranging */

    /** Gym (program 2) and CrossFit (program 3) are switched on, so Today has three program chips. */
    protected suspend fun activateStarters() {
        container.programs.setActive(2, true).getOrThrow()
        container.programs.setActive(3, true).getOrThrow()
    }

    protected val programIds: List<String>
        get() = runBlocking {
            container.catalog.observeAll().first()
                .filter { it.slot.name == "PROGRAM" }.map { it.id }
        }

    protected suspend fun completeCircuit(week: Int, day: Int, circuit: Int, ids: List<String> = programIds) {
        ids.forEach { container.repo.setExerciseDone(week, day, circuit, it, true) }
    }

    protected suspend fun completeDay(week: Int, day: Int) {
        val circuits = container.rules.rulesFor(container.repo.ensureActiveCycle()).circuitsForWeek(week)
        (1..circuits).forEach { completeCircuit(week, day, it) }
    }

    /** Saves [transform] of the current draft and applies it to the running cycle. */
    protected suspend fun applyRules(
        units: UnitPrefs = UnitPrefs(),
        transform: (ProgramRules) -> ProgramRules = { it }
    ) {
        val cycleId = container.repo.ensureActiveCycle()
        val errors = container.rules.saveDraft(transform(container.rules.getDraft()), units)
        check(errors.isEmpty()) { errors.toString() }
        container.rules.applyDraftToCycle(cycleId)
    }

    protected suspend fun editExercise(id: String, transform: (ExerciseDraft) -> ExerciseDraft) {
        val e = container.catalog.observeAll(includeArchived = true).first().first { it.id == id }
        container.catalog.update(id, transform(e.toDraft())).getOrThrow()
    }

    private fun Exercise.toDraft() = ExerciseDraft(
        name = name, category = category, slot = slot, muscles = muscles,
        instructions = instructions, targetType = targetType, targetValue = targetValue,
        perSide = perSide, targetLabel = targetLabel, videoUrl = videoUrl,
        tracksReps = tracksReps, tracksLoad = tracksLoad, defaultLoadKg = defaultLoadKg,
        defaultBandLevel = defaultBandLevel, progressionStep = progressionStep, enabled = enabled
    )

    /* ------------------------------------------------------------ driving */

    protected fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = TIMEOUT) {
        rule.waitUntil(timeoutMs) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    protected fun waitGone(matcher: SemanticsMatcher, timeoutMs: Long = TIMEOUT) {
        rule.waitUntil(timeoutMs) { rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
    }

    protected fun waitForText(text: String, substring: Boolean = false) =
        waitFor(hasText(text, substring = substring))

    protected fun waitForDesc(desc: String, substring: Boolean = false) =
        waitFor(hasContentDescription(desc, substring = substring))

    /** First node matching [matcher], scrolled into view (plain or lazy scroll containers). */
    protected fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        val composed = runCatching { waitFor(matcher, 2_000) }.isSuccess
        if (!composed) {
            // Not composed: it is further down a lazy list. Try each one on screen.
            val lists = rule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().size
            for (i in 0 until lists) {
                val ok = runCatching {
                    rule.onAllNodes(hasScrollToNodeAction())[i].performScrollToNode(matcher)
                }.isSuccess
                if (ok) break
            }
        }
        waitFor(matcher)
        return rule.onAllNodes(matcher).onFirst().also { runCatching { it.performScrollTo() } }
    }

    protected fun text(text: String, substring: Boolean = false) =
        node(hasText(text, substring = substring))

    protected fun desc(desc: String, substring: Boolean = false) =
        node(hasContentDescription(desc, substring = substring))

    protected fun tapText(text: String, substring: Boolean = false) {
        text(text, substring).performClick()
        rule.waitForIdle()
    }

    protected fun tapDesc(desc: String, substring: Boolean = false) {
        desc(desc, substring).performClick()
        rule.waitForIdle()
    }

    /** A dialog's own button, when the screen behind has a same-named one. */
    protected fun tapInDialog(label: String) {
        node(hasText(label) and hasAnyAncestor(isDialog())).performClick()
        rule.waitForIdle()
    }

    /** PrimaryButton/GhostButton render their label upper-cased; pass it as written in code. */
    protected fun button(label: String) = text(label.uppercase())

    protected fun tapButton(label: String) = tapText(label.uppercase())

    protected fun seeButton(label: String) = see(label.uppercase())

    /** Asserts [text] is on screen, waiting for async data to land. */
    protected fun see(text: String, substring: Boolean = false) {
        text(text, substring)
    }

    protected fun seeDesc(desc: String, substring: Boolean = false) {
        desc(desc, substring)
    }

    /** Asserts [text] is not on screen, waiting for it to leave if async data is still landing. */
    protected fun dontSee(text: String, substring: Boolean = false) {
        rule.waitForIdle()
        val gone = runCatching { waitGone(hasText(text, substring = substring)) }.isSuccess
        check(gone) { "Expected no \"$text\" on screen" }
    }

    /** A text field, found by its label. */
    protected fun field(label: String): SemanticsNodeInteraction =
        node(hasSetTextAction() and hasText(label, substring = true))

    /** Waits until the field labelled [label] holds exactly [value] (pre-fills load async). */
    protected fun seeFieldValue(label: String, value: String) {
        node(hasSetTextAction() and hasText(label, substring = true) and hasText(value))
    }

    protected fun typeInto(label: String, value: String) {
        field(label).performTextReplacement(value)
        rule.waitForIdle()
    }

    protected fun back() {
        Espresso.pressBack()
        rule.waitForIdle()
    }

    protected fun openTab(label: String) = tapText(label)

    protected companion object {
        const val TIMEOUT = 5_000L
    }
}
