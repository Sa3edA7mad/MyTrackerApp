package com.example.mytrackerapp.ui.screens.circuit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.repo.SetDetail
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary

/** An exercise waiting on the log sheet before its completion (and advance) is written. */
private data class PendingLog(
    val exercise: Exercise,
    /** Most recently logged detail, loaded before the sheet opens to pre-fill it. */
    val lastDetail: SetDetail?,
    val onLogged: () -> Unit
)

@Composable
fun CircuitRoute(
    programId: Long,
    week: Int,
    day: Int,
    circuit: Int,
    onExit: () -> Unit,
    viewModel: CircuitViewModel = viewModel(
        factory = CircuitViewModel.factory(programId, week, day, circuit),
        key = "circuit/$programId/$week/$day/$circuit"
    )
) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val unitPrefs by viewModel.unitPrefs.collectAsState()
    val result by viewModel.result.collectAsState()

    var showSummary by remember { mutableStateOf(false) }
    var restartKey by remember { mutableIntStateOf(0) }
    var pendingLog by remember { mutableStateOf<PendingLog?>(null) }
    val scope = rememberCoroutineScope()
    // The exercise opened from list view, run on its own in the guided pager.
    var focusId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = focusId != null) { focusId = null }

    when (val s = state) {
        is UiState.Loading -> LoadingState()

        is UiState.Error -> Box(
            Modifier.fillMaxSize().padding(Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                s.message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        is UiState.Ready -> Column(Modifier.fillMaxSize()) {
            val view = s.data
            val name = view.plan?.name?.takeIf { it.isNotBlank() && it != "Circuit" }
            val overline = "CIRCUIT $circuit · " + (name?.let { "${it.uppercase()} · W$week D$day" } ?: "WEEK $week DAY $day")
            view.plan?.let { plan ->
                WorkoutClock(
                    plan,
                    slotKey = "$programId/$week/$day/$circuit",
                    savedResult = result,
                    onSaveResult = viewModel::saveResult
                )
            }
            Box(Modifier.weight(1f)) {

            fun requestDone(exerciseId: String, onLogged: () -> Unit) {
                val exercise = view.exercises.firstOrNull { it.id == exerciseId }
                if (exercise != null && (exercise.tracksReps || exercise.tracksLoad)) {
                    scope.launch {
                        pendingLog = PendingLog(exercise, viewModel.lastDetail(exercise.id), onLogged)
                    }
                } else {
                    viewModel.setDone(exerciseId, true)
                    onLogged()
                }
            }

            val focus = focusId
            if (focus != null) {
                GuidedPager(
                    // List view may complete a locked future day, so its single-exercise
                    // run may too.
                    view = view.copy(editable = true),
                    settings = settings,
                    overline = overline,
                    onDone = { id, onLogged -> requestDone(id, onLogged) },
                    onExit = { focusId = null },
                    onFinished = { focusId = null },
                    onSwitchToChecklist = null,
                    startExerciseId = focus,
                    singleExercise = true
                )
            } else if (settings.guidedMode) {
                GuidedPager(
                    view = view,
                    settings = settings,
                    overline = overline,
                    onDone = { id, onLogged -> requestDone(id, onLogged) },
                    onExit = onExit,
                    onFinished = { showSummary = true },
                    onSwitchToChecklist = { viewModel.setGuidedMode(false) },
                    restartKey = restartKey
                )
            } else {
                ChecklistMode(
                    view = view,
                    title = "Circuit $circuit" + (name?.let { " · $it" } ?: ""),
                    hapticsEnabled = settings.haptics,
                    onToggle = { id, done ->
                        if (done) requestDone(id) {} else viewModel.setDone(id, false)
                    },
                    onExit = onExit,
                    onSwitchToGuided = { viewModel.setGuidedMode(true) },
                    onOpenExercise = { focusId = it },
                    onCompleteAll = {
                        viewModel.completeAll(
                            view.exercises.map { it.id }.filterNot { it in view.doneIds }
                        )
                    }
                )
            }

            pendingLog?.let { pending ->
                LogSetSheet(
                    exercise = pending.exercise,
                    week = view.week,
                    unitPrefs = unitPrefs,
                    lastDetail = pending.lastDetail,
                    onSave = { detail ->
                        viewModel.setDone(pending.exercise.id, true, detail)
                        pendingLog = null
                        pending.onLogged()
                    },
                    onSkip = {
                        viewModel.setDone(pending.exercise.id, true, null)
                        pendingLog = null
                        pending.onLogged()
                    }
                )
            }

            if (showSummary) {
                // The last DONE writes asynchronously, so re-check against the freshest
                // view rather than a value captured when the pager reached the end.
                if (view.isComplete) {
                    LaunchedEffect(Unit) {
                        showSummary = false
                        onExit()
                    }
                } else {
                    AlertDialog(
                        onDismissRequest = { showSummary = false },
                        containerColor = SurfaceColor,
                        titleContentColor = TextPrimary,
                        textContentColor = TextSecondary,
                        title = { Text("${view.done} of ${view.total} done") },
                        text = {
                            Text(
                                "You skipped ${view.total - view.done} " +
                                    "exercise${if (view.total - view.done > 1) "s" else ""}. " +
                                    "You can go back to them now, or leave this circuit partial " +
                                    "and pick it up later."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showSummary = false
                                restartKey += 1
                            }) { Text("BACK TO MISSED", color = Accent) }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showSummary = false
                                onExit()
                            }) { Text("FINISH ANYWAY", color = TextSecondary) }
                        }
                    )
                }
            }
            }
        }
    }
}
