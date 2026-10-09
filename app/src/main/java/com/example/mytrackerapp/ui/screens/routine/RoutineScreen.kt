package com.example.mytrackerapp.ui.screens.routine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mytrackerapp.TrackerApplication
import com.example.mytrackerapp.data.prefs.Settings
import com.example.mytrackerapp.data.prefs.SettingsStore
import com.example.mytrackerapp.domain.CIRCUIT_STRETCH
import com.example.mytrackerapp.domain.CIRCUIT_WARMUP
import com.example.mytrackerapp.domain.model.CircuitView
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.repo.TrackerRepository
import com.example.mytrackerapp.ui.RoutineType
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.screens.circuit.ChecklistMode
import com.example.mytrackerapp.ui.screens.circuit.GuidedPager
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.TextSecondary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutineViewModel(
    private val repo: TrackerRepository,
    private val settingsStore: SettingsStore,
    private val routineCircuit: Int
) : ViewModel() {

    val state: StateFlow<UiState<CircuitView>> = repo.observeRoutine(routineCircuit)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setDone(exerciseId: String, done: Boolean) {
        viewModelScope.launch { repo.setRoutineExerciseDone(routineCircuit, exerciseId, done) }
    }

    /** Sets the day flag. Used both by "finished" and by "skip" — see RoutineRoute. */
    fun completeAll(exerciseIds: List<String>) {
        viewModelScope.launch {
            exerciseIds.forEach { repo.setRoutineExerciseDone(routineCircuit, it, true) }
        }
    }

    /** Same global default as circuits: switching here switches everywhere. */
    fun setGuidedMode(guided: Boolean) {
        viewModelScope.launch { settingsStore.setGuidedMode(guided) }
    }

    fun markDone() {
        viewModelScope.launch { repo.markRoutineDone(routineCircuit) }
    }

    companion object {
        fun factory(programId: Long, routineCircuit: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                RoutineViewModel(app.container.repoFor(programId), app.container.settings, routineCircuit)
            }
        }
    }
}

/**
 * Warm-up and stretch.
 *
 * Follows the same guided / list preference as circuits. The guided pager sets the day
 * flag when you reach the end; list view sets it once every move is ticked, and its
 * "Done with …" button sets it early (the flag records "I warmed up", not a tally).
 */
@Composable
fun RoutineRoute(
    type: RoutineType,
    onExit: () -> Unit,
    programId: Long = 1
) {
    val routineCircuit = when (type) {
        RoutineType.WARMUP -> CIRCUIT_WARMUP
        RoutineType.STRETCH -> CIRCUIT_STRETCH
    }
    val label = when (type) {
        RoutineType.WARMUP -> "WARM-UP"
        RoutineType.STRETCH -> "STRETCH"
    }

    val viewModel: RoutineViewModel = viewModel(
        factory = RoutineViewModel.factory(programId, routineCircuit),
        key = "routine/$programId/${type.slug}"
    )
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // The move opened from list view, run on its own in the guided pager.
    var focusId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = focusId != null) { focusId = null }

    when (val s = state) {
        is UiState.Loading -> LoadingState()

        is UiState.Error -> Column(
            Modifier.fillMaxSize().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            GhostButton("Back", onExit)
            Spacer(Modifier.height(Spacing.xl))
        }

        is UiState.Ready -> {
            val view = s.data
            val focus = focusId

            if (!settings.guidedMode) {
                LaunchedEffect(view.isComplete) {
                    if (view.total > 0 && view.isComplete) viewModel.markDone()
                }
            }

            if (focus != null) {
                GuidedPager(
                    view = view.copy(editable = true),
                    settings = settings,
                    overline = "$label · WEEK ${view.week} DAY ${view.day}",
                    onDone = { id, onLogged -> viewModel.setDone(id, true); onLogged() },
                    onExit = { focusId = null },
                    onFinished = { focusId = null },
                    onSwitchToChecklist = null,
                    startExerciseId = focus,
                    singleExercise = true
                )
            } else if (!settings.guidedMode) {
                ChecklistMode(
                    view = view,
                    title = label.lowercase().replaceFirstChar { it.uppercase() },
                    hapticsEnabled = settings.haptics,
                    onToggle = { id, done -> viewModel.setDone(id, done) },
                    onExit = onExit,
                    onSwitchToGuided = { viewModel.setGuidedMode(true) },
                    onOpenExercise = { focusId = it },
                    onCompleteAll = {
                        viewModel.completeAll(
                            view.exercises.map { it.id }.filterNot { it in view.doneIds }
                        )
                    },
                    finishAction = "Done with ${label.lowercase()}" to {
                        viewModel.markDone()
                        onExit()
                    }
                )
            } else {
                GuidedPager(
                    view = view,
                    settings = settings,
                    overline = "$label · WEEK ${view.week} DAY ${view.day}",
                    onDone = { id, onLogged -> viewModel.setDone(id, true); onLogged() },
                    onExit = onExit,
                    onFinished = {
                        // Reaching the end counts as done even if individual moves were
                        // skipped — the day flag records "I warmed up", not a per-move tally.
                        viewModel.markDone()
                        onExit()
                    },
                    onSwitchToChecklist = { viewModel.setGuidedMode(false) },
                    secondaryAction = "Skip ${label.lowercase()}" to {
                        viewModel.markDone()
                        onExit()
                    }
                )
            }
        }
    }
}
