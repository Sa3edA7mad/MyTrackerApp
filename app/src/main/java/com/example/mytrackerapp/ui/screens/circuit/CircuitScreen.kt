package com.example.mytrackerapp.ui.screens.circuit

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary

@Composable
fun CircuitRoute(
    week: Int,
    day: Int,
    circuit: Int,
    onExit: () -> Unit,
    viewModel: CircuitViewModel = viewModel(
        factory = CircuitViewModel.factory(week, day, circuit),
        key = "circuit/$week/$day/$circuit"
    )
) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var showSummary by remember { mutableStateOf(false) }
    var restartKey by remember { mutableIntStateOf(0) }

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

        is UiState.Ready -> {
            val view = s.data

            if (settings.guidedMode) {
                GuidedPager(
                    view = view,
                    settings = settings,
                    overline = "CIRCUIT $circuit · WEEK $week DAY $day",
                    onDone = { id -> viewModel.setDone(id, true) },
                    onExit = onExit,
                    onFinished = { showSummary = true },
                    onSwitchToChecklist = { viewModel.setGuidedMode(false) },
                    restartKey = restartKey
                )
            } else {
                ChecklistMode(
                    view = view,
                    title = "Circuit $circuit",
                    hapticsEnabled = settings.haptics,
                    onToggle = { id, done -> viewModel.setDone(id, done) },
                    onExit = onExit,
                    onSwitchToGuided = { viewModel.setGuidedMode(true) }
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
