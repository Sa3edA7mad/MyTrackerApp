package com.example.mytrackerapp.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mytrackerapp.TrackerApplication
import com.example.mytrackerapp.di.AppContainer
import com.example.mytrackerapp.domain.model.Program
import com.example.mytrackerapp.domain.model.TodayView
import com.example.mytrackerapp.domain.model.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Today for whichever active program is selected. [programs] empty means none is active. */
data class TodayUi(
    val programs: List<Program>,
    val programId: Long?,
    val today: UiState<TodayView>
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    /** Seeded from the last choice, so Today reopens on the program you left it on. */
    private val selected = MutableStateFlow<Long?>(null)
    private val restored = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            selected.value = container.settings.todayProgramId.first()
            restored.value = true
        }
    }

    val state: StateFlow<UiState<TodayUi>> =
        combine(container.programs.observeActive(), selected, restored) { programs, chosen, ready ->
            // Hold on the loading state until the saved choice is read, so Today never flashes
            // the first program and then jumps.
            if (!ready) null
            else programs to (programs.firstOrNull { it.id == chosen } ?: programs.firstOrNull())?.id
        }.filterNotNull().flatMapLatest { (programs, id) ->
            if (id == null) flowOf(UiState.Ready(TodayUi(programs, null, UiState.Loading)))
            else container.repoFor(id).observeToday().map { UiState.Ready(TodayUi(programs, id, it)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun select(programId: Long) {
        selected.value = programId
        viewModelScope.launch { container.settings.setTodayProgramId(programId) }
    }

    fun closeDayEarly(programId: Long, week: Int, day: Int) {
        viewModelScope.launch { container.repoFor(programId).closeDayEarly(week, day) }
    }

    /** Lets Today move past a finished day without stretching. Writes no completions. */
    fun skipStretch(programId: Long, week: Int, day: Int) {
        viewModelScope.launch { container.repoFor(programId).markStretchDone(week, day) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                TodayViewModel(app.container)
            }
        }
    }
}
