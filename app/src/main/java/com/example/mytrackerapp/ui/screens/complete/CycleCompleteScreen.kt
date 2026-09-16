package com.example.mytrackerapp.ui.screens.complete

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mytrackerapp.TrackerApplication
import com.example.mytrackerapp.domain.model.CycleSummary
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.repo.TrackerRepository
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.components.PrimaryButton
import com.example.mytrackerapp.ui.components.ProgressRing
import com.example.mytrackerapp.ui.components.StatTile
import com.example.mytrackerapp.ui.components.StickyCtaBar
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CycleCompleteViewModel(private val repo: TrackerRepository) : ViewModel() {

    val state: StateFlow<UiState<CycleSummary>> = repo.observeCycleSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /**
     * Closes the finished cycle and opens a fresh one. Completions are never deleted —
     * the old cycle stays in the database so past cycles remain comparable.
     */
    fun startNewCycle(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.startNewCycle()
            onDone()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                CycleCompleteViewModel(app.container.repo)
            }
        }
    }
}

@Composable
fun CycleCompleteRoute(
    onStartNewCycle: () -> Unit,
    onBack: () -> Unit,
    viewModel: CycleCompleteViewModel = viewModel(factory = CycleCompleteViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is UiState.Loading -> LoadingState()
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.message, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        is UiState.Ready -> CycleCompleteScreen(
            summary = s.data,
            onStartNewCycle = { viewModel.startNewCycle(onStartNewCycle) },
            onBack = onBack
        )
    }
}

@Composable
fun CycleCompleteScreen(
    summary: CycleSummary,
    onStartNewCycle: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(CanvasColor)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.xxl))
            Text(
                "CYCLE COMPLETE",
                style = MaterialTheme.typography.labelSmall,
                color = Accent
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Four weeks done",
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.xl))
            ProgressRing(
                progress = summary.percent / 100f,
                diameter = 160.dp,
                contentDescription = "${summary.percent} percent of the cycle completed"
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${summary.percent}%",
                        style = MaterialTheme.typography.displayMedium,
                        color = Accent
                    )
                    Text(
                        "OF THE PROGRAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                StatTile("${summary.exercisesDone}", "Exercises", Modifier.weight(1f))
                StatTile("${summary.circuitsDone}", "Circuits", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                StatTile("${summary.daysTrained}", "Days trained", Modifier.weight(1f))
                StatTile("${summary.bestStreak}", "Best streak", Modifier.weight(1f))
                StatTile("${summary.elapsedDays}", "Days elapsed", Modifier.weight(1f))
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = when {
                    summary.isPerfect ->
                        "Every one of ${summary.exercisesTotal} exercises. Nothing left on the table."
                    summary.daysClosedEarly > 0 ->
                        "${summary.exercisesDone} of ${summary.exercisesTotal} exercises, " +
                            "with ${summary.daysClosedEarly} day" +
                            "${if (summary.daysClosedEarly == 1) "" else "s"} ended early. " +
                            "Finished is finished."
                    else ->
                        "${summary.exercisesDone} of ${summary.exercisesTotal} exercises across " +
                            "${summary.daysTrained} training days."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.lg))
        }

        StickyCtaBar {
            PrimaryButton("Start a new cycle", onStartNewCycle)
            GhostButton("Not yet", onBack, Modifier.fillMaxWidth())
        }
    }
}

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 880)
@Composable
private fun CycleCompletePerfectPreview() {
    MyTrackerAppTheme {
        CycleCompleteScreen(
            summary = CycleSummary(
                exercisesDone = 1716, exercisesTotal = 1716,
                circuitsDone = 132, circuitsTotal = 132,
                daysTrained = 24, bestStreak = 11, elapsedDays = 29, daysClosedEarly = 0
            ),
            onStartNewCycle = {}, onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 880)
@Composable
private fun CycleCompletePartialPreview() {
    MyTrackerAppTheme {
        CycleCompleteScreen(
            summary = CycleSummary(
                exercisesDone = 1204, exercisesTotal = 1716,
                circuitsDone = 92, circuitsTotal = 132,
                daysTrained = 21, bestStreak = 8, elapsedDays = 34, daysClosedEarly = 3
            ),
            onStartNewCycle = {}, onBack = {}
        )
    }
}
