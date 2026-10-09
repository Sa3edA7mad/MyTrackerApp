package com.example.mytrackerapp.ui.screens.programedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import com.example.mytrackerapp.domain.CircuitPlan
import com.example.mytrackerapp.domain.PlanValidation
import com.example.mytrackerapp.domain.ProgramPlan
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.model.Program
import com.example.mytrackerapp.ui.components.ActionRow
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.Chip
import com.example.mytrackerapp.ui.components.ConfirmDialog
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.SettingRow
import com.example.mytrackerapp.ui.screens.program.NameDialog
import com.example.mytrackerapp.ui.theme.Danger
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary
import com.example.mytrackerapp.ui.theme.glassBackdrop
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-line description of a circuit for the editor lists. */
internal fun CircuitPlan.summary(): String {
    val what = when {
        slot != null -> "Every enabled ${slot.lowercase()} exercise in the Library"
        items.isEmpty() -> "No exercises yet"
        else -> "${items.size} exercise${if (items.size == 1) "" else "s"} · $size set${if (size == 1) "" else "s"}"
    }
    val format = when (format) {
        CircuitFormat.STANDARD -> if (restSeconds > 0) " · rest ${restSeconds}s" else ""
        CircuitFormat.EMOM -> " · EMOM $rounds min"
        CircuitFormat.INTERVAL -> " · ${workSeconds}s on / ${restSeconds}s off × $rounds"
        CircuitFormat.AMRAP -> " · AMRAP $minutes min"
        CircuitFormat.FOR_TIME -> " · for time" + if (minutes > 0) " (cap $minutes min)" else ""
    }
    return what + format
}

class ProgramEditViewModel(private val container: AppContainer, val programId: Long) : ViewModel() {

    private val rulesRepo = container.rulesFor(programId)

    val program: StateFlow<Program?> = container.programs.observe(programId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val plan: StateFlow<ProgramPlan?> = rulesRepo.observePlan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rules: StateFlow<ProgramRules?> = rulesRepo.observeDraft()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    /** The outcome of the last action — an error, or a confirmation. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun rename(name: String) = viewModelScope.launch {
        container.programs.rename(programId, name).onFailure { _message.value = it.message }
    }

    fun setActive(active: Boolean) = viewModelScope.launch {
        container.programs.setActive(programId, active)
            .onSuccess { _message.value = null }
            .onFailure { _message.value = "Can't turn this program on yet: ${it.message}" }
    }

    /** Adds or removes [key] from [day]'s rotation, keeping plan order. */
    fun toggleDayCircuit(day: Int, key: String) = viewModelScope.launch {
        val p = plan.value ?: return@launch
        val days = p.days.toMutableList()
        while (days.size < day) days += emptyList<String>()
        val current = days[day - 1].toSet()
        val next = if (key in current) current - key else current + key
        days[day - 1] = p.circuits.map { it.key }.filter { it in next }
        rulesRepo.savePlan(p.copy(days = days))
    }

    fun applyToCurrentCycle() = viewModelScope.launch {
        val repo = container.repoFor(programId)
        val errors = rulesRepo.applyDraftToCycle(repo.ensureActiveCycle())
        _message.value = errors.firstOrNull() ?: "Applied — the current cycle now runs this plan."
    }

    fun resetCycle() = viewModelScope.launch {
        container.repoFor(programId).resetActiveCycle()
        _message.value = "This cycle's progress was reset."
    }

    fun setArchived(archived: Boolean) = viewModelScope.launch {
        if (archived) container.programs.archive(programId) else container.programs.restore(programId)
    }

    companion object {
        fun factory(programId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                ProgramEditViewModel(app.container, programId)
            }
        }
    }
}

@Composable
fun ProgramEditRoute(
    programId: Long,
    onBack: () -> Unit,
    onEditCircuit: (key: String) -> Unit,
    onOpenRules: () -> Unit,
    viewModel: ProgramEditViewModel = viewModel(
        factory = ProgramEditViewModel.factory(programId),
        key = "programEdit/$programId"
    )
) {
    val program by viewModel.program.collectAsState()
    val plan by viewModel.plan.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val message by viewModel.message.collectAsState()

    val p = program
    val pl = plan
    val r = rules
    if (p == null || pl == null || r == null) {
        LoadingState()
        return
    }
    ProgramEditScreen(
        program = p,
        plan = pl,
        rules = r,
        message = message,
        onBack = onBack,
        onRename = viewModel::rename,
        onActiveChange = viewModel::setActive,
        onEditCircuit = onEditCircuit,
        onToggleDayCircuit = viewModel::toggleDayCircuit,
        onOpenRules = onOpenRules,
        onApply = viewModel::applyToCurrentCycle,
        onReset = viewModel::resetCycle,
        onArchive = viewModel::setArchived
    )
}

@Composable
fun ProgramEditScreen(
    program: Program,
    plan: ProgramPlan,
    rules: ProgramRules,
    message: String?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onEditCircuit: (String) -> Unit,
    onToggleDayCircuit: (day: Int, key: String) -> Unit,
    onOpenRules: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onArchive: (Boolean) -> Unit
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val problems = PlanValidation.validate(plan)

    Column(
        Modifier
            .fillMaxSize()
            .glassBackdrop()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
    ) {
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(RoundedCornerShape(Radius.pill))
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(AppIcons.back), contentDescription = "Back", tint = TextTertiary, modifier = Modifier.size(22.dp))
        }
        Text(program.name, style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Text(
            "${rules.weeks} weeks · ${rules.daysPerWeek} days a week · ${plan.circuits.size} circuit" +
                if (plan.circuits.size == 1) "" else "s",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )
        message?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        if (problems.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            problems.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = Danger) }
        }

        SectionHeader("Program")
        ActionRow(title = "Name", subtitle = program.name, onClick = { renaming = true })
        if (!program.archived) {
            SettingRow(
                "Active",
                if (program.active) "Shown on Today" else "Paused — its cycle waits where it is",
                program.active,
                onActiveChange
            )
        }
        ActionRow(
            title = "Rules",
            subtitle = "Weeks, days, rounds per day, warm-up and stretch toggles",
            onClick = onOpenRules
        )

        SectionHeader("Circuits")
        plan.circuits.forEach { c ->
            ActionRow(title = c.name, subtitle = c.summary(), onClick = { onEditCircuit(c.key) })
        }
        ActionRow(title = "+ Add circuit", subtitle = "A named group of exercises", onClick = { onEditCircuit(NEW_CIRCUIT) })

        if (plan.circuits.size > 1) {
            SectionHeader("Days")
            Text(
                "Pick the circuits each day runs. A day with none picked runs every circuit.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
            (1..rules.daysPerWeek).forEach { day ->
                val picked = plan.days.getOrNull(day - 1).orEmpty()
                Spacer(Modifier.height(Spacing.sm))
                Text("Day $day", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    plan.circuits.forEach { c ->
                        Chip(c.name, c.key in picked) { onToggleDayCircuit(day, c.key) }
                    }
                }
            }
        }

        SectionHeader("Routines")
        ActionRow(title = "Warm-up", subtitle = plan.warmUp.summary(), onClick = { onEditCircuit(ProgramPlan.WARMUP_KEY) })
        ActionRow(title = "Stretch", subtitle = plan.stretch.summary(), onClick = { onEditCircuit(ProgramPlan.STRETCH_KEY) })

        SectionHeader("Current cycle")
        Text(
            "Edits apply to the next cycle. Apply them now to change the cycle you're in.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )
        ActionRow(title = "Apply changes to current cycle", subtitle = "Keeps every finished set", onClick = { confirmApply = true })
        ActionRow(
            title = "Reset this cycle's progress",
            subtitle = "Clears this program's ticks; other programs are untouched",
            tint = Danger,
            onClick = { confirmReset = true }
        )
        ActionRow(
            title = if (program.archived) "Restore program" else "Archive program",
            subtitle = if (program.archived) "Back to the list, paused" else "Hidden from Today; history is kept",
            tint = if (program.archived) TextPrimary else Danger,
            onClick = { onArchive(!program.archived) }
        )
        Spacer(Modifier.height(Spacing.xxl))
    }

    if (renaming) {
        NameDialog(
            title = "Rename program",
            initial = program.name,
            onConfirm = { onRename(it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
    if (confirmApply) {
        ConfirmDialog(
            title = "Apply to current cycle?",
            body = "The cycle you're in switches to this plan and these rules. Finished sets are kept; " +
                "ones that no longer fit stop counting.",
            confirmLabel = "APPLY",
            onConfirm = { confirmApply = false; onApply() },
            onDismiss = { confirmApply = false },
            destructive = false
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset this cycle?",
            body = "Every tick in ${program.name}'s current cycle is deleted. This can't be undone.",
            confirmLabel = "RESET",
            onConfirm = { confirmReset = false; onReset() },
            onDismiss = { confirmReset = false }
        )
    }
}

/** Route key meaning "create a circuit". */
const val NEW_CIRCUIT = "new"
