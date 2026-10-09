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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.example.mytrackerapp.di.AppContainer
import com.example.mytrackerapp.domain.model.Program
import com.example.mytrackerapp.ui.components.ChipRow
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.theme.Danger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.example.mytrackerapp.ui.theme.glassBackdrop

/** The Programs tab: every program, and the selected one's week grid. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramViewModel(private val container: AppContainer) : ViewModel() {

    private val selected = MutableStateFlow<Long?>(null)

    /** Active programs first, then paused, then archived. */
    val programs: StateFlow<List<Program>> = container.programs.observeAll()
        .map { list -> list.sortedWith(compareBy({ it.archived }, { !it.active }, { it.id })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val programId: StateFlow<Long> = combine(programs, selected) { list, chosen ->
        (list.firstOrNull { it.id == chosen } ?: list.firstOrNull())?.id ?: 1L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1L)

    val state: StateFlow<UiState<List<WeekState>>> = programId
        .flatMapLatest { container.repoFor(it).observeProgram() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val current: StateFlow<Position?> = programId
        .flatMapLatest { container.repoFor(it).observeCurrentPosition() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The running cycle's INVARIANT 4 toggle, so previews are only labelled when locked. */
    val lockFutureDays: StateFlow<Boolean> = programId
        .flatMapLatest { container.repoFor(it).observeActiveRules() }
        .map { it.lockFutureDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun select(id: Long) {
        selected.value = id
    }

    fun create(name: String, onCreated: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            container.programs.create(name)
                .onSuccess { selected.value = it; onCreated(it) }
                .onFailure { onError(it.message ?: "Couldn't create the program.") }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                ProgramViewModel(app.container)
            }
        }
    }
}

@Composable
fun ProgramRoute(
    onOpenCircuit: (programId: Long, week: Int, day: Int, circuit: Int) -> Unit,
    onEditProgram: (programId: Long) -> Unit = {},
    viewModel: ProgramViewModel = viewModel(factory = ProgramViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    val current by viewModel.current.collectAsState()
    val lockFutureDays by viewModel.lockFutureDays.collectAsState()
    val programs by viewModel.programs.collectAsState()
    val programId by viewModel.programId.collectAsState()
    var naming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val header: @Composable () -> Unit = {
        ProgramsHeader(
            programs = programs,
            selected = programs.firstOrNull { it.id == programId },
            onSelect = { viewModel.select(it.id) },
            onEdit = { onEditProgram(programId) },
            onNew = { naming = true }
        )
    }

    when (val s = state) {
        is UiState.Loading -> LoadingState()
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.message, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }

        is UiState.Ready -> ProgramScreen(
            weeks = s.data,
            current = current,
            onOpenCircuit = { w, d, c -> onOpenCircuit(programId, w, d, c) },
            lockFutureDays = lockFutureDays,
            header = header
        )
    }

    if (naming) {
        NameDialog(
            title = "New program",
            error = error,
            onConfirm = { name ->
                viewModel.create(
                    name,
                    onCreated = { naming = false; error = null; onEditProgram(it) },
                    onError = { error = it }
                )
            },
            onDismiss = { naming = false; error = null }
        )
    }
}

@Composable
private fun ProgramsHeader(
    programs: List<Program>,
    selected: Program?,
    onSelect: (Program) -> Unit,
    onEdit: () -> Unit,
    onNew: () -> Unit
) {
    ChipRow(
        options = programs,
        selected = selected,
        label = { p -> p.name + when { p.archived -> " · archived"; !p.active -> " · paused"; else -> "" } },
        onSelect = onSelect
    )
    Spacer(Modifier.height(Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        GhostButton("Edit program", onClick = onEdit, modifier = Modifier.weight(1f))
        GhostButton("+ New program", onClick = onNew, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(Spacing.md))
}

/** A one-field name prompt, shared by "New program" and "Add circuit". */
@Composable
fun NameDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    initial: String = ""
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = Danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("SAVE", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextSecondary) }
        }
    )
}

@Composable
fun ProgramScreen(
    weeks: List<WeekState>,
    current: Position?,
    onOpenCircuit: (week: Int, day: Int, circuit: Int) -> Unit,
    lockFutureDays: Boolean = true,
    /** Program picker and actions, between the title and the summary line. */
    header: @Composable () -> Unit = {}
) {
    var expanded by remember { mutableStateOf<Position?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().glassBackdrop().padding(horizontal = Spacing.lg)
    ) {
        item {
            Spacer(Modifier.height(Spacing.sm))
            Text("Programs", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
            Spacer(Modifier.height(Spacing.sm))
            header()
            val daysPerWeek = weeks.firstOrNull()?.days?.size ?: 0
            Text(
                "${weeks.size} week${if (weeks.size == 1) "" else "s"} · " +
                    "$daysPerWeek day${if (daysPerWeek == 1) "" else "s"} a week · " +
                    "${weeks.sumOf { it.circuitsTotal }} circuits",
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
                lockFutureDays = lockFutureDays,
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
    lockFutureDays: Boolean,
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
                    if (week.days.map { it.circuits.size }.distinct().size <= 1) "${week.circuitsPerDay} circuits/day · ${week.days.size} days"
                    else "${week.circuitsTotal} circuits · ${week.days.size} days",
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
            val isFuture = lockFutureDays && current != null &&
                isAfter(Position(open.week, open.day), current)
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
                    subtitle = circuit.subtitle,
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
 * Mirrors the repository's INVARIANT 4 rule so the UI can label previews. Program order is
 * week-major, so this holds for any rule shape — unlike indexing into a fixed rule set.
 */
private fun isAfter(candidate: Position, current: Position): Boolean =
    compareValuesBy(candidate, current, { it.week }, { it.day }) > 0

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF4A5052, widthDp = 400, heightDp = 880)
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
