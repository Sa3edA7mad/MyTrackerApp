package com.example.mytrackerapp.ui.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.model.CircuitProgress
import com.example.mytrackerapp.domain.model.CycleStats
import com.example.mytrackerapp.domain.model.DaySummary
import com.example.mytrackerapp.domain.model.ExerciseTally
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.domain.model.WeekState
import com.example.mytrackerapp.repo.TrackerRepository
import com.example.mytrackerapp.ui.components.ActionRow
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.StatTile
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.HeatPartial
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.SurfaceHigh
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ProgressViewModel(repo: TrackerRepository) : ViewModel() {

    val state: StateFlow<UiState<CycleStats>> = repo.observeCycleStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                ProgressViewModel(app.container.repo)
            }
        }
    }
}

@Composable
fun ProgressRoute(
    onOpenMeasure: () -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is UiState.Loading -> LoadingState()
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.message, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        is UiState.Ready -> ProgressScreen(s.data, onOpenMeasure)
    }
}

@Composable
fun ProgressScreen(stats: CycleStats, onOpenMeasure: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CanvasColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
    ) {
        Spacer(Modifier.height(Spacing.sm))
        Text("Progress", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Text(
            "Current cycle",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )

        Spacer(Modifier.height(Spacing.base))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile("${stats.streak}", "Streak", Modifier.weight(1f))
            StatTile("${stats.percent}%", "Cycle", Modifier.weight(1f))
            StatTile("${stats.exercisesDone}", "Ex. done", Modifier.weight(1f))
        }

        SectionHeader("4-week map")
        HeatMap(stats.heat)
        Spacer(Modifier.height(Spacing.sm))
        Legend()

        SectionHeader("Circuits per week")
        stats.weeks.forEach { week ->
            WeekBar(week)
            Spacer(Modifier.height(Spacing.md))
        }

        SectionHeader("Health")
        ActionRow(
            title = "Body measurements",
            subtitle = "Weight, girths, resting heart rate and derived stats",
            onClick = onOpenMeasure
        )

        SectionHeader("Most done")
        if (stats.mostDone.isEmpty()) {
            Text(
                "Complete your first circuit to start a streak.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        } else {
            Text(
                stats.mostDone.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Text(
                "${stats.mostDone.first().count} completions",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun HeatMap(heat: List<DaySummary>) {
    // Derived from the data rather than a fixed constant, so the grid follows whatever
    // shape the active cycle's rules describe.
    val weeks = heat.map { it.week }.distinct().sorted()
    val daysPerWeek = heat.map { it.day }.distinct().sorted()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(Modifier.size(26.dp))
            daysPerWeek.forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "D$day",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
        weeks.forEach { week ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(26.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        "W$week",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
                daysPerWeek.forEach { day ->
                    val cell = heat.firstOrNull { it.week == week && it.day == day }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(
                                when {
                                    cell == null -> SurfaceHigh
                                    cell.isComplete -> Accent
                                    cell.isPartial -> HeatPartial
                                    else -> SurfaceHigh
                                }
                            )
                            .semantics {
                                contentDescription = "Week $week day $day, " + when {
                                    cell == null || cell.isUntouched -> "not started"
                                    cell.isComplete -> "complete"
                                    else -> "${cell.done} of ${cell.total}"
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.base)) {
        LegendItem(Accent, "Complete")
        LegendItem(HeatPartial, "Partial")
        LegendItem(SurfaceHigh, "Not yet")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
}

@Composable
private fun WeekBar(week: WeekState) {
    val fraction =
        if (week.circuitsTotal == 0) 0f else week.circuitsDone.toFloat() / week.circuitsTotal
    Column {
        Row {
            Text(
                "Week ${week.week} · ${week.circuitsPerDay}/day",
                style = MaterialTheme.typography.bodyMedium,
                color = if (week.circuitsDone > 0) TextSecondary else TextTertiary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${week.circuitsDone}/${week.circuitsTotal}",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceHigh)
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Accent)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ previews */

private fun previewStats(empty: Boolean): CycleStats {
    val rules = ProgramRules.DEFAULT
    fun day(w: Int, d: Int, done: Int): DaySummary {
        val circuitsInWeek = rules.circuitsForWeek(w)
        return DaySummary(
            week = w, day = d, done = done,
            total = circuitsInWeek * 13, closed = false,
            circuits = (1..circuitsInWeek).map {
                CircuitProgress(
                    index = it,
                    done = if (done >= it * 13) 13 else 0,
                    total = 13
                )
            }
        )
    }

    val heat = (1..rules.weeks).flatMap { w ->
        (1..rules.daysPerWeek).map { d ->
            val done = when {
                empty -> 0
                w == 1 -> rules.circuitsForWeek(w) * 13
                w == 2 && d < 3 -> rules.circuitsForWeek(w) * 13
                w == 2 && d == 3 -> 26
                else -> 0
            }
            day(w, d, done)
        }
    }
    return CycleStats(
        streak = if (empty) 0 else 11,
        exercisesDone = heat.sumOf { it.done },
        exercisesTotal = 1716,
        circuitsDone = heat.sumOf { it.done / 13 },
        circuitsTotal = 132,
        daysTrained = if (empty) 0 else 9,
        weeks = (1..rules.weeks).map { w ->
            WeekState(
                week = w,
                circuitsPerDay = rules.circuitsForWeek(w),
                days = heat.filter { it.week == w },
                isCurrent = w == 2,
                exercisesPerCircuit = 13
            )
        },
        heat = heat,
        mostDone = if (empty) emptyList() else listOf(
            ExerciseTally("squat", "Squat", 47),
            ExerciseTally("push_up", "Push-up", 47),
            ExerciseTally("dead_hang", "Dead Hang", 47)
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 880)
@Composable
private fun ProgressPreview() {
    MyTrackerAppTheme { ProgressScreen(previewStats(empty = false)) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 880)
@Composable
private fun ProgressEmptyPreview() {
    MyTrackerAppTheme { ProgressScreen(previewStats(empty = true)) }
}
