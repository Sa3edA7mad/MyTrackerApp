package com.example.mytrackerapp.ui.screens.circuit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs both a program circuit and the two once-a-day routines — they are the same
 * screen with a different slice of the catalog, distinguished by [circuit].
 */
class CircuitViewModel(
    private val repo: TrackerRepository,
    private val settingsStore: SettingsStore,
    private val week: Int,
    private val day: Int,
    private val circuit: Int
) : ViewModel() {

    val state: StateFlow<UiState<CircuitView>> = repo.observeCircuit(week, day, circuit)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setDone(exerciseId: String, done: Boolean) {
        viewModelScope.launch { repo.setExerciseDone(week, day, circuit, exerciseId, done) }
    }

    /** Switching mode mid-circuit also becomes the new global default. */
    fun setGuidedMode(guided: Boolean) {
        viewModelScope.launch { settingsStore.setGuidedMode(guided) }
    }

    /** Marks the day-level warm-up / stretch flag. No-op for a program circuit. */
    fun markRoutineDone() {
        viewModelScope.launch {
            when (circuit) {
                CIRCUIT_WARMUP -> repo.markWarmUpDone(week, day)
                CIRCUIT_STRETCH -> repo.markStretchDone(week, day)
                else -> Unit
            }
        }
    }

    companion object {
        fun factory(week: Int, day: Int, circuit: Int): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                            as TrackerApplication
                    CircuitViewModel(
                        repo = app.container.repo,
                        settingsStore = app.container.settings,
                        week = week,
                        day = day,
                        circuit = circuit
                    )
                }
            }
    }
}
