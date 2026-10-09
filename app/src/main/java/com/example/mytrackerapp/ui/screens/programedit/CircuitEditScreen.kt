package com.example.mytrackerapp.ui.screens.programedit

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mytrackerapp.TrackerApplication
import com.example.mytrackerapp.di.AppContainer
import com.example.mytrackerapp.domain.CircuitFormat
import com.example.mytrackerapp.domain.CircuitItem
import com.example.mytrackerapp.domain.CircuitOrder
import com.example.mytrackerapp.domain.CircuitPlan
import com.example.mytrackerapp.domain.PlanValidation
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.Units
import com.example.mytrackerapp.domain.WeightUnit
import com.example.mytrackerapp.domain.ProgramPlan
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.TargetType
import com.example.mytrackerapp.domain.resolve
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.ChipRow
import com.example.mytrackerapp.ui.components.ConfirmDialog
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.components.LabeledField
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.components.NumberStepper
import com.example.mytrackerapp.ui.components.PrimaryButton
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.StickyCtaBar
import com.example.mytrackerapp.ui.components.label
import com.example.mytrackerapp.ui.screens.library.filterCatalog
import com.example.mytrackerapp.ui.theme.Danger
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary
import com.example.mytrackerapp.ui.theme.glassBackdrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Edits one circuit of a program's draft plan — or its warm-up or stretch, by key. */
class CircuitEditViewModel(container: AppContainer, programId: Long, private val key: String) : ViewModel() {

    private val rulesRepo = container.rulesFor(programId)
    val isRoutine = key == ProgramPlan.WARMUP_KEY || key == ProgramPlan.STRETCH_KEY

    private val _circuit = MutableStateFlow<CircuitPlan?>(null)
    val circuit: StateFlow<CircuitPlan?> = _circuit.asStateFlow()

    /** Whether this circuit can be deleted: a real, saved circuit that isn't the only one. */
    var deletable = false
        private set

    /** Loads are entered in the display unit and stored in kilograms. */
    val units: StateFlow<UnitPrefs> = rulesRepo.observeUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitPrefs())

    val catalog: StateFlow<List<Exercise>> = container.catalog.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val plan = rulesRepo.getPlan()
            deletable = !isRoutine && key != NEW_CIRCUIT && plan.circuits.size > 1
            _circuit.value = when (key) {
                ProgramPlan.WARMUP_KEY -> plan.warmUp
                ProgramPlan.STRETCH_KEY -> plan.stretch
                NEW_CIRCUIT -> {
                    val n = plan.circuits.size + 1
                    val used = plan.circuits.map { it.key }.toSet()
                    CircuitPlan(
                        key = generateSequence(n) { it + 1 }.map { "c$it" }.first { it !in used },
                        name = "Circuit ${('A' + (n - 1) % 26)}"
                    )
                }
                else -> plan.circuits.firstOrNull { it.key == key } ?: CircuitPlan(key, "Circuit")
            }
        }
    }

    fun update(transform: (CircuitPlan) -> CircuitPlan) {
        _circuit.value = _circuit.value?.let(transform)
    }

    fun updateItem(index: Int, transform: (CircuitItem) -> CircuitItem) = update { c ->
        c.copy(items = c.items.mapIndexed { i, item -> if (i == index) transform(item) else item })
    }

    fun moveItem(index: Int, delta: Int) = update { c ->
        val to = index + delta
        if (to !in c.items.indices) c
        else c.copy(items = c.items.toMutableList().also { it.add(to, it.removeAt(index)) })
    }

    fun removeItem(index: Int) = update { c -> c.copy(items = c.items.filterIndexed { i, _ -> i != index }) }

    fun addExercise(id: String) = update { c ->
        if (c.items.any { it.exerciseId == id }) c else c.copy(items = c.items + CircuitItem(id))
    }

    /** Turns a Library-slot circuit into its current explicit list, ready to edit. */
    fun chooseExercises() = update { c ->
        ProgramPlan(listOf(c)).resolve(catalog.value).circuits.first()
    }

    /**
     * Write-then-leave runs in [viewModelScope] (main dispatcher), not the screen's scope: a
     * composition coroutine can resume off the main thread, and navigating from there crashes.
     */
    fun save(onDone: () -> Unit) = viewModelScope.launch { save(); onDone() }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { delete(); onDone() }

    /** Saves into the draft plan; returns what's still unfinished (it saves regardless). */
    suspend fun save(): List<String> {
        val c = _circuit.value ?: return emptyList()
        val plan = rulesRepo.getPlan()
        val next = when (key) {
            ProgramPlan.WARMUP_KEY -> plan.copy(warmUp = c)
            ProgramPlan.STRETCH_KEY -> plan.copy(stretch = c)
            else -> if (plan.circuits.any { it.key == c.key }) {
                plan.copy(circuits = plan.circuits.map { if (it.key == c.key) c else it })
            } else {
                plan.copy(circuits = plan.circuits + c)
            }
        }
        return rulesRepo.savePlan(next)
    }

    suspend fun delete() {
        val plan = rulesRepo.getPlan()
        rulesRepo.savePlan(
            plan.copy(
                circuits = plan.circuits.filter { it.key != key },
                days = plan.days.map { day -> day.filter { it != key } }
            )
        )
    }

    companion object {
        fun factory(programId: Long, key: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                CircuitEditViewModel(app.container, programId, key)
            }
        }
    }
}

@Composable
fun CircuitEditRoute(
    programId: Long,
    circuitKey: String,
    onDone: () -> Unit,
    viewModel: CircuitEditViewModel = viewModel(
        factory = CircuitEditViewModel.factory(programId, circuitKey),
        key = "circuitEdit/$programId/$circuitKey"
    )
) {
    val circuit by viewModel.circuit.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val units by viewModel.units.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val c = circuit
    if (c == null) {
        LoadingState()
        return
    }
    val byId = catalog.associateBy { it.id }

    Column(Modifier.fillMaxSize().glassBackdrop()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
        ) {
            Box(
                Modifier
                    .size(MinTouchTarget)
                    .clip(RoundedCornerShape(Radius.pill))
                    .clickable(role = Role.Button, onClick = onDone),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(AppIcons.back), contentDescription = "Back", tint = TextTertiary, modifier = Modifier.size(22.dp))
            }
            Text(c.name.ifBlank { "Circuit" }, style = MaterialTheme.typography.displayMedium, color = TextPrimary)
            Text(c.summary(), style = MaterialTheme.typography.bodyMedium, color = TextTertiary)

            if (!viewModel.isRoutine) {
                Spacer(Modifier.height(Spacing.md))
                LabeledField("Name", c.name, { v -> viewModel.update { it.copy(name = v) } })
            }

            SectionHeader("Exercises")
            if (c.slot != null) {
                Text(
                    "This list is every enabled exercise in the Library's ${c.slot.lowercase()} slot, in Library " +
                        "order — edit it there, or pick exercises here to set sets and targets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(Spacing.sm))
                GhostButton("Choose exercises instead", onClick = viewModel::chooseExercises, modifier = Modifier.fillMaxWidth())
            } else {
                c.items.forEachIndexed { index, item ->
                    ItemCard(
                        item = item,
                        exercise = byId[item.exerciseId],
                        weightUnit = units.weight,
                        canMoveUp = index > 0,
                        canMoveDown = index < c.items.lastIndex,
                        onChange = { t -> viewModel.updateItem(index, t) },
                        onMove = { d -> viewModel.moveItem(index, d) },
                        onRemove = { viewModel.removeItem(index) }
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                GhostButton("+ Add exercise", onClick = { picking = true }, modifier = Modifier.fillMaxWidth())
            }

            if (!viewModel.isRoutine) {
                SectionHeader("Set order")
                ChipRow(
                    CircuitOrder.entries,
                    c.order,
                    { if (it == CircuitOrder.ROUNDS) "Rounds" else "Straight sets" },
                    { o -> viewModel.update { it.copy(order = o) } }
                )
                Text(
                    if (c.order == CircuitOrder.ROUNDS) "One set of each exercise, then round again."
                    else "Every set of an exercise before the next one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = Spacing.xs)
                )

                SectionHeader("Format")
                ChipRow(
                    CircuitFormat.entries,
                    c.format,
                    { formatLabel(it) },
                    { f -> viewModel.update { it.copy(format = f) } }
                )
                Spacer(Modifier.height(Spacing.sm))
                when (c.format) {
                    CircuitFormat.STANDARD ->
                        SecondsField("Rest between sets (seconds)", c.restSeconds) { v -> viewModel.update { it.copy(restSeconds = v) } }
                    CircuitFormat.EMOM ->
                        SecondsField("Minutes", c.rounds) { v -> viewModel.update { it.copy(rounds = v) } }
                    CircuitFormat.INTERVAL -> {
                        SecondsField("Work (seconds)", c.workSeconds) { v -> viewModel.update { it.copy(workSeconds = v) } }
                        SecondsField("Rest (seconds)", c.restSeconds) { v -> viewModel.update { it.copy(restSeconds = v) } }
                        SecondsField("Rounds", c.rounds) { v -> viewModel.update { it.copy(rounds = v) } }
                    }
                    CircuitFormat.AMRAP ->
                        SecondsField("Minutes", c.minutes) { v -> viewModel.update { it.copy(minutes = v) } }
                    CircuitFormat.FOR_TIME ->
                        SecondsField("Time cap (minutes, 0 = none)", c.minutes) { v -> viewModel.update { it.copy(minutes = v) } }
                }
            }

            val problems = PlanValidation.validate(ProgramPlan(listOf(c)))
            if (!viewModel.isRoutine && problems.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                problems.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Danger) }
            }
            if (viewModel.deletable) {
                Spacer(Modifier.height(Spacing.md))
                GhostButton("Delete circuit", onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), tint = Danger)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
        StickyCtaBar {
            PrimaryButton("Save", onClick = { viewModel.save(onDone) })
        }
    }

    if (picking) {
        ExercisePicker(
            catalog = catalog.filter { e -> c.items.none { it.exerciseId == e.id } },
            onPick = { viewModel.addExercise(it); picking = false },
            onDismiss = { picking = false }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${c.name}?",
            body = "It's removed from the plan and from every day that runs it. A running cycle keeps it until you apply.",
            confirmLabel = "DELETE",
            onConfirm = { confirmDelete = false; viewModel.delete(onDone) },
            onDismiss = { confirmDelete = false }
        )
    }
}

private fun formatLabel(f: CircuitFormat) = when (f) {
    CircuitFormat.STANDARD -> "Standard"
    CircuitFormat.EMOM -> "EMOM"
    CircuitFormat.INTERVAL -> "Interval"
    CircuitFormat.AMRAP -> "AMRAP"
    CircuitFormat.FOR_TIME -> "For time"
}

@Composable
private fun SecondsField(label: String, value: Int, onChange: (Int) -> Unit) {
    LabeledField(
        label,
        if (value == 0) "" else value.toString(),
        { v -> onChange(v.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0) },
        keyboardType = KeyboardType.Number,
        modifier = Modifier.padding(bottom = Spacing.sm)
    )
}

@Composable
private fun ItemCard(
    item: CircuitItem,
    exercise: Exercise?,
    weightUnit: WeightUnit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: ((CircuitItem) -> CircuitItem) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceColor)
            .border(1.dp, Outline, RoundedCornerShape(Radius.md))
            .padding(Spacing.md)
    ) {
        Text(exercise?.name ?: item.exerciseId, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        if (exercise == null) {
            Text("Archived or missing — skipped", style = MaterialTheme.typography.bodyMedium, color = Danger)
        }
        NumberStepper("Sets", item.sets, 1..PlanValidation.MAX_SETS, { v -> onChange { it.copy(sets = v) } })
        if (exercise != null) {
            val unit = if (exercise.targetType == TargetType.REPS) "reps" else "sec"
            NumberStepper(
                "Target ($unit)",
                item.target ?: exercise.targetValue,
                1..999,
                { v -> onChange { it.copy(target = v.takeIf { t -> t != exercise.targetValue }) } }
            )
            if (exercise.tracksLoad) {
                LoadField(weightUnit, item.loadKg) { kg -> onChange { it.copy(loadKg = kg) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
            GhostButton("↑", onClick = { onMove(-1) }, modifier = Modifier.weight(1f), enabled = canMoveUp)
            GhostButton("↓", onClick = { onMove(1) }, modifier = Modifier.weight(1f), enabled = canMoveDown)
            GhostButton("Remove", onClick = onRemove, modifier = Modifier.weight(2f), tint = Danger)
        }
    }
}

/** Search the Library and pick one exercise. */
@Composable
private fun ExercisePicker(catalog: List<Exercise>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        titleContentColor = TextPrimary,
        title = { Text("Add exercise") },
        text = {
            Column {
                LabeledField("Search name, muscle or equipment", query, { query = it })
                Spacer(Modifier.height(Spacing.sm))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filterCatalog(catalog, query), key = { it.id }) { e ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinTouchTarget)
                                .clickable(role = Role.Button) { onPick(e.id) }
                                .padding(vertical = Spacing.sm)
                        ) {
                            Text(e.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                e.category.label + if (e.equipment.isNotBlank()) " · ${e.equipment}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = TextSecondary) } }
    )
}

/**
 * A load override typed in the display unit, stored in kilograms. The typed text is kept
 * locally so converting back and forth never rewrites what the user is in the middle of typing.
 */
@Composable
private fun LoadField(unit: WeightUnit, loadKg: Double?, onKg: (Double?) -> Unit) {
    var text by remember(unit) {
        mutableStateOf(
            // One decimal is all the field offers, so show exactly that — 99.79 kg is "220", not "220.0000001".
            loadKg?.let { Math.round(Units.kgToDisplay(it, unit) * 10) / 10.0 }
                ?.let { if (it % 1.0 == 0.0) it.toLong().toString() else Units.format(it, 1) }
                .orEmpty()
        )
    }
    LabeledField(
        "Load (${unit.label}) — blank for the library default",
        text,
        { v ->
            text = v
            onKg(v.replace(',', '.').toDoubleOrNull()?.let { Units.displayToKg(it, unit) })
        },
        keyboardType = KeyboardType.Decimal
    )
}
