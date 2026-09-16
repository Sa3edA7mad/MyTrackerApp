package com.example.mytrackerapp.ui.screens.routine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    fun markDone() {
        viewModelScope.launch { repo.markRoutineDone(routineCircuit) }
    }

    companion object {
        fun factory(routineCircuit: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                RoutineViewModel(app.container.repo, app.container.settings, routineCircuit)
            }
        }
    }
}

/**
 * Warm-up and stretch.
 *
 * Deliberately guided-only (`onSwitchToChecklist = null`): these are eight short moves,
 * the stretches are timed holds that want the countdown dial, and a flat checklist would
 * have no natural "I'm finished" action to hang the day flag on.
 */
@Composable
fun RoutineRoute(
    type: RoutineType,
    onExit: () -> Unit
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
        factory = RoutineViewModel.factory(routineCircuit),
        key = "routine/${type.slug}"
    )
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()

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
            GuidedPager(
                view = view,
                settings = settings,
                overline = "$label · WEEK ${view.week} DAY ${view.day}",
                onDone = { id -> viewModel.setDone(id, true) },
                onExit = onExit,
                onFinished = {
                    // Reaching the end counts as done even if individual moves were
                    // skipped — the day flag records "I warmed up", not a per-move tally.
                    viewModel.markDone()
                    onExit()
                },
                onSwitchToChecklist = null,
                secondaryAction = "Skip ${label.lowercase()}" to {
                    viewModel.markDone()
                    onExit()
                }
            )
        }
    }
}
