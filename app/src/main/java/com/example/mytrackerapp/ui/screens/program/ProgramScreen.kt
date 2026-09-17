package com.example.mytrackerapp.ui.screens.program

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
import com.example.mytrackerapp.domain.Position
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.model.CircuitProgress
import com.example.mytrackerapp.domain.model.DaySummary
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.domain.model.WeekState
import com.example.mytrackerapp.repo.TrackerRepository
import com.example.mytrackerapp.ui.components.CircuitCard
import com.example.mytrackerapp.ui.components.CircuitCardState
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.HeatPartial
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.OnAccent
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.SurfaceHigh
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ProgramViewModel(repo: TrackerRepository) : ViewModel() {

    val state: StateFlow<UiState<List<WeekState>>> = repo.observeProgram()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val current: StateFlow<Position?> = repo.observeCurrentPosition()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                ProgramViewModel(app.container.repo)
            }
        }
    }
}

@Composable
fun ProgramRoute(
    onOpenCircuit: (week: Int, day: Int, circuit: Int) -> Unit,
    viewModel: ProgramViewModel = viewModel(factory = ProgramViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    val current by viewModel.current.collectAsState()

    when (val s = state) {
        is UiState.Loading -> LoadingState()
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.message, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        is UiState.Ready -> ProgramScreen(
            weeks = s.data,
            current = current,
            onOpenCircuit = onOpenCircuit
        )
    }
}

@Composable
fun ProgramScreen(
    weeks: List<WeekState>,
    current: Position?,
    onOpenCircuit: (week: Int, day: Int, circuit: Int) -> Unit
) {
    var expanded by remember { mutableStateOf<Position?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(CanvasColor).padding(horizontal = Spacing.lg)
    ) {
        item {
            Spacer(Modifier.height(Spacing.sm))
            Text("Program", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
            Text(
                "4 weeks · 6 days a week · 132 circuits",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
            Spacer(Modifier.height(Spacing.md))
        }

        items(weeks.size, key = { weeks[it].week }) { i ->
            val week = weeks[i]
            WeekCard(
                week = week,
                current = current,
                expandedDay = expanded?.takeIf { it.week == week.week }?.day,
                onToggleDay = { day ->
                    val p = Position(week.week, day)
                    expanded = if (expanded == p) null else p
                },
                onOpenCircuit = onOpenCircuit
            )
            Spacer(Modifier.height(Spacing.md))
        }

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}

@Composable
private fun WeekCard(
    week: WeekState,
    current: Position?,
    expandedDay: Int?,
    onToggleDay: (Int) -> Unit,
    onOpenCircuit: (Int, Int, Int) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(SurfaceColor)
            .border(
                width = if (week.isCurrent) 1.5.dp else 1.dp,
                color = if (week.isCurrent) Accent else Outline,
                shape = RoundedCornerShape(Radius.lg)
            )
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "WEEK ${week.week}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (week.isCurrent) Accent else TextTertiary
                )
                Text(
                    "${week.circuitsPerDay} circuits/day · 6 days",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Text(
                "${week.circuitsDone}/${week.circuitsTotal}",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
        }

        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            week.days.forEach { day ->
                DaySquare(
                    day = day,
                    isCurrent = current?.week == day.week && current.day == day.day,
                    isSelected = expandedDay == day.day,
                    onClick = { onToggleDay(day.day) }
                )
            }
        }

        val open = week.days.firstOrNull { it.day == expandedDay }
        if (open != null) {
            Spacer(Modifier.height(Spacing.md))
            val isFuture = current != null && isAfter(Position(open.week, open.day), current)
            Text(
                if (isFuture) {
                    "Day ${open.day} · preview — finish the current day first"
                } else {
                    "Day ${open.day} · ${open.done}/${open.total} exercises"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isFuture) TextTertiary else TextSecondary
            )
            Spacer(Modifier.height(Spacing.sm))
            open.circuits.forEach { circuit ->
                CircuitCard(
                    index = circuit.index,
                    done = circuit.done,
                    total = circuit.total,
                    state = when {
                        circuit.isComplete -> CircuitCardState.DONE
                        isFuture -> CircuitCardState.LOCKED
                        else -> CircuitCardState.UPCOMING
                    },
                    onClick = { onOpenCircuit(open.week, open.day, circuit.index) },
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
            }
        }
    }
}

@Composable
private fun DaySquare(
    day: DaySummary,
    isCurrent: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val fill = when {
        day.isComplete -> Accent
        day.isPartial -> HeatPartial
        else -> SurfaceHigh
    }
    val label = buildString {
        append("Week ${day.week} day ${day.day}, ")
        append(
            when {
                day.isComplete -> "complete"
                day.closed -> "ended early, ${day.done} of ${day.total}"
                day.isPartial -> "${day.done} of ${day.total}"
                else -> "not started"
            }
        )
    }
    Box(
        Modifier
            .size(MinTouchTarget)
            .clip(RoundedCornerShape(Radius.md))
            .background(fill)
            .then(
                when {
                    isSelected -> Modifier.border(2.dp, TextPrimary, RoundedCornerShape(Radius.md))
                    isCurrent -> Modifier.border(1.5.dp, Accent, RoundedCornerShape(Radius.md))
                    else -> Modifier
                }
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${day.day}",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                day.isComplete -> OnAccent
                day.isPartial -> TextPrimary
                isCurrent -> Accent
                else -> TextTertiary
            },
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Mirrors the repository's INVARIANT 4 rule so the UI can label previews.
 *
 * Uses [ProgramRules.DEFAULT] rather than the live rules — this screen doesn't yet receive
 * the active rule set. T11 wires `lockFutureDays` through properly; until then this matches
 * today's fixed behaviour exactly.
 */
private fun isAfter(candidate: Position, current: Position): Boolean =
    ProgramRules.DEFAULT.isAfter(candidate, current)

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 880)
@Composable
private fun ProgramPreview() {
    val rules = ProgramRules.DEFAULT
    fun day(w: Int, d: Int, done: Int) = DaySummary(
        week = w, day = d, done = done,
        total = rules.circuitsForWeek(w) * rules.exercisesPerCircuit, closed = false,
        circuits = (1..rules.circuitsForWeek(w)).map {
            CircuitProgress(
                index = it,
                done = if (done >= it * rules.exercisesPerCircuit) rules.exercisesPerCircuit else 0,
                total = rules.exercisesPerCircuit
            )
        }
    )
    MyTrackerAppTheme {
        ProgramScreen(
            weeks = listOf(
                WeekState(1, 4, (1..6).map { day(1, it, 52) }, isCurrent = false, exercisesPerCircuit = 13),
                WeekState(
                    2, 5,
                    (1..6).map { day(2, it, if (it < 3) 65 else if (it == 3) 26 else 0) },
                    isCurrent = true,
                    exercisesPerCircuit = 13
                ),
                WeekState(3, 6, (1..6).map { day(3, it, 0) }, isCurrent = false, exercisesPerCircuit = 13),
                WeekState(4, 7, (1..6).map { day(4, it, 0) }, isCurrent = false, exercisesPerCircuit = 13)
            ),
            current = Position(2, 3),
            onOpenCircuit = { _, _, _ -> }
        )
    }
}
