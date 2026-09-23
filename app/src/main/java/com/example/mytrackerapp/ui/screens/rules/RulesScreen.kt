package com.example.mytrackerapp.ui.screens.rules

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
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mytrackerapp.TrackerApplication
import com.example.mytrackerapp.domain.ApplyImpact
import com.example.mytrackerapp.domain.LengthUnit
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.RuleValidation
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.WeightUnit
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.repo.RulesRepository
import com.example.mytrackerapp.repo.TrackerRepository
import com.example.mytrackerapp.ui.components.ActionRow
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.ConfirmDialog
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.components.NumberStepper
import com.example.mytrackerapp.ui.components.PrimaryButton
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.SettingRow
import com.example.mytrackerapp.ui.components.StickyCtaBar
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.Danger
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.OnAccent
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the screen needs, recomputed together so the impact line never lags the draft. */
data class RulesEditorState(
    val draft: ProgramRules,
    val units: UnitPrefs,
    val errors: List<String>,
    val impact: ApplyImpact?
)

class RulesViewModel(
    private val rulesRepo: RulesRepository,
    private val trackerRepo: TrackerRepository
) : ViewModel() {

    private var cycleId: Long? = null
    private val draft = MutableStateFlow<ProgramRules?>(null)
    private val units = MutableStateFlow(UnitPrefs())
    private val impact = MutableStateFlow<ApplyImpact?>(null)

    val state: StateFlow<UiState<RulesEditorState>> =
        combine(draft, units, impact) { d, u, i ->
            if (d == null) UiState.Loading
            else UiState.Ready(RulesEditorState(d, u, RuleValidation.validate(d), i))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        viewModelScope.launch {
            cycleId = trackerRepo.ensureActiveCycle()
            units.value = rulesRepo.observeUnits().first()
            draft.value = rulesRepo.getDraft()
            recomputeImpact()
        }
    }

    private fun recomputeImpact() {
        val id = cycleId ?: return
        val d = draft.value ?: return
        viewModelScope.launch { impact.value = rulesRepo.previewApply(id, d) }
    }

    fun updateDraft(transform: (ProgramRules) -> ProgramRules) {
        draft.value = draft.value?.let(transform)
        recomputeImpact()
    }

    fun updateUnits(transform: (UnitPrefs) -> UnitPrefs) {
        units.value = transform(units.value)
    }

    /** Returns validation errors; empty means it saved. */
    suspend fun save(): List<String> {
        val d = draft.value ?: return listOf("Nothing to save.")
        return rulesRepo.saveDraft(d, units.value)
    }

    /** Saves, then re-snapshots the active cycle. Returns validation errors; empty means it applied. */
    suspend fun applyToCurrentCycle(): List<String> {
        val id = cycleId ?: return listOf("No active cycle.")
        val errors = save()
        if (errors.isNotEmpty()) return errors
        return rulesRepo.applyDraftToCycle(id)
    }

    fun restoreDefaults() {
        viewModelScope.launch {
            rulesRepo.restoreDefaultRules()
            draft.value = rulesRepo.getDraft()
            units.value = rulesRepo.observeUnits().first()
            recomputeImpact()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                RulesViewModel(app.container.rules, app.container.repo)
            }
        }
    }
}

@Composable
fun RulesRoute(
    onBack: () -> Unit,
    viewModel: RulesViewModel = viewModel(factory = RulesViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }

    when (val s = state) {
        is UiState.Loading -> LoadingState()
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.message, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }
        is UiState.Ready -> RulesScreen(
            editor = s.data,
            snackbars = snackbars,
            onBack = onBack,
            onDraftChange = viewModel::updateDraft,
            onUnitsChange = viewModel::updateUnits,
            onSave = {
                scope.launch {
                    val errors = viewModel.save()
                    snackbars.showSnackbar(
                        if (errors.isEmpty()) "Saved for the next cycle." else errors.first()
                    )
                }
            },
            onApply = {
                scope.launch {
                    val errors = viewModel.applyToCurrentCycle()
                    snackbars.showSnackbar(
                        if (errors.isEmpty()) "Applied to the current cycle." else errors.first()
                    )
                }
            },
            onRestoreDefaults = viewModel::restoreDefaults
        )
    }
}

@Composable
fun RulesScreen(
    editor: RulesEditorState,
    snackbars: SnackbarHostState,
    onBack: () -> Unit,
    onDraftChange: ((ProgramRules) -> ProgramRules) -> Unit,
    onUnitsChange: ((UnitPrefs) -> UnitPrefs) -> Unit,
    onSave: () -> Unit,
    onApply: () -> Unit,
    onRestoreDefaults: () -> Unit
) {
    val rules = editor.draft
    var confirmApply by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = CanvasColor,
        snackbarHost = { SnackbarHost(snackbars) }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
            ) {
                Box(
                    Modifier
                        .size(MinTouchTarget)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        painterResource(AppIcons.back),
                        contentDescription = "Back",
                        tint = TextTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text("Program rules", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
                Text(
                    "${rules.weeks} weeks · ${rules.daysPerWeek} days · ${rules.exercisesPerCircuit} exercises a circuit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )

                SectionHeader("Shape")
                NumberStepper(
                    "Weeks",
                    rules.weeks,
                    1..RuleValidation.MAX_WEEKS,
                    { v -> onDraftChange { it.withWeeks(v) } }
                )
                Spacer(Modifier.height(Spacing.sm))
                NumberStepper(
                    "Days per week",
                    rules.daysPerWeek,
                    1..RuleValidation.MAX_DAYS_PER_WEEK,
                    { v -> onDraftChange { it.copy(daysPerWeek = v) } }
                )

                SectionHeader("Circuits a day")
                rules.circuitsPerWeek.forEachIndexed { index, count ->
                    NumberStepper(
                        "Week ${index + 1}",
                        count,
                        1..RuleValidation.MAX_CIRCUITS_PER_DAY,
                        { v ->
                            onDraftChange { r ->
                                r.copy(circuitsPerWeek = r.circuitsPerWeek.toMutableList().also { it[index] = v })
                            }
                        }
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }

                SectionHeader("Routines")
                SettingRow(
                    "Warm-up enabled",
                    "${rules.warmUpCount} moves before your first circuit",
                    rules.warmUpEnabled,
                    { v -> onDraftChange { it.copy(warmUpEnabled = v) } }
                )
                SettingRow(
                    "Stretch enabled",
                    "${rules.stretchCount} stretches after your last circuit",
                    rules.stretchEnabled,
                    { v -> onDraftChange { it.copy(stretchEnabled = v) } }
                )

                SectionHeader("Counting")
                SettingRow(
                    "Count warm-up and stretch in totals",
                    "Off means the ${rules.warmUpCount + rules.stretchCount} warm-up and stretch " +
                        "moves are tracked but never part of a day, week or cycle total",
                    rules.countRoutinesInTotals,
                    { v -> onDraftChange { it.copy(countRoutinesInTotals = v) } }
                )
                SettingRow(
                    "Lock future days",
                    "Off lets you edit any day at any time, not just the current one",
                    rules.lockFutureDays,
                    { v -> onDraftChange { it.copy(lockFutureDays = v) } }
                )
                NumberStepper(
                    "Day rollover hour",
                    rules.dayRolloverHour,
                    0..23,
                    { v -> onDraftChange { it.copy(dayRolloverHour = v) } },
                    suffix = ":00"
                )

                SectionHeader("Units")
                Text(
                    "Display only — your history is stored in kilograms and centimetres and never converts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
                Spacer(Modifier.height(Spacing.sm))
                UnitToggleRow(
                    label = "Weight",
                    options = WeightUnit.entries.map { it.label },
                    selectedIndex = editor.units.weight.ordinal,
                    onSelect = { i -> onUnitsChange { it.copy(weight = WeightUnit.entries[i]) } }
                )
                Spacer(Modifier.height(Spacing.sm))
                UnitToggleRow(
                    label = "Length",
                    options = LengthUnit.entries.map { it.label },
                    selectedIndex = editor.units.length.ordinal,
                    onSelect = { i -> onUnitsChange { it.copy(length = LengthUnit.entries[i]) } }
                )

                SectionHeader("Impact")
                ImpactSummary(editor.impact)

                if (editor.errors.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    editor.errors.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Danger)
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                ActionRow(
                    title = "Restore default rules",
                    subtitle = "Back to 4 weeks · 6 days · 13 exercises a circuit",
                    tint = Danger,
                    onClick = { confirmRestore = true }
                )
                Spacer(Modifier.height(Spacing.xxl))
            }

            StickyCtaBar {
                GhostButton(
                    text = "Apply to current cycle",
                    onClick = { confirmApply = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editor.errors.isEmpty()
                )
                PrimaryButton(
                    text = "Save for next cycle",
                    onClick = onSave,
                    enabled = editor.errors.isEmpty()
                )
            }
        }
    }

    if (confirmApply) {
        val impact = editor.impact
        ConfirmDialog(
            title = "Apply to the current cycle?",
            body = buildString {
                append("This re-snapshots the running cycle to match the rules above. ")
                append("Nothing is ever deleted. ")
                if (impact != null && !impact.isLossless) {
                    append("${impact.daysReopened} day(s) would reopen and ")
                    append("${impact.orphanedCompletions} completion(s) would stop counting.")
                } else {
                    append("This change is lossless.")
                }
            },
            confirmLabel = "APPLY",
            destructive = impact?.isLossless == false,
            onConfirm = { confirmApply = false; onApply() },
            onDismiss = { confirmApply = false }
        )
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "Restore default rules?",
            body = "This rewrites the draft back to the shipped 4-week program. " +
                "It does not touch the currently running cycle until you apply it.",
            confirmLabel = "RESTORE",
            onConfirm = { confirmRestore = false; onRestoreDefaults() },
            onDismiss = { confirmRestore = false }
        )
    }
}

/** Growing/shrinking [ProgramRules.weeks] keeps existing per-week values and pads new
 *  weeks with the last week's circuit count. */
private fun ProgramRules.withWeeks(newWeeks: Int): ProgramRules {
    val current = circuitsPerWeek
    val padded = when {
        newWeeks == current.size -> current
        newWeeks < current.size -> current.take(newWeeks)
        else -> current + List(newWeeks - current.size) { current.lastOrNull() ?: 4 }
    }
    return copy(weeks = newWeeks, circuitsPerWeek = padded)
}

@Composable
private fun ImpactSummary(impact: ApplyImpact?) {
    if (impact == null) {
        Text("Calculating…", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
        return
    }
    Column {
        Text(
            "${impact.oldTotal} → ${impact.newTotal} exercises · ${impact.newDays} days",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        if (!impact.isLossless) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                buildString {
                    if (impact.daysReopened > 0) append("${impact.daysReopened} day(s) would reopen. ")
                    if (impact.orphanedCompletions > 0) {
                        append("${impact.orphanedCompletions} completion(s) would stop counting " +
                            "(nothing is deleted).")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Accent
            )
        }
    }
}

@Composable
private fun UnitToggleRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .border(1.dp, Outline, RoundedCornerShape(Radius.pill))
        ) {
            options.forEachIndexed { index, label2 ->
                val selected = index == selectedIndex
                Box(
                    Modifier
                        .heightIn(min = MinTouchTarget)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(if (selected) Accent else Color.Transparent)
                        .clickable(role = Role.Button, onClick = { onSelect(index) })
                        .padding(horizontal = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label2.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) OnAccent else TextSecondary
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 900)
@Composable
private fun RulesScreenPreview() {
    MyTrackerAppTheme {
        RulesScreen(
            editor = RulesEditorState(
                draft = ProgramRules.DEFAULT,
                units = UnitPrefs(),
                errors = emptyList(),
                impact = ApplyImpact(
                    oldTotal = 1716, newTotal = 1716, oldDays = 24, newDays = 24,
                    daysReopened = 0, orphanedCompletions = 0, positionMovesBack = false
                )
            ),
            snackbars = remember { SnackbarHostState() },
            onBack = {},
            onDraftChange = {},
            onUnitsChange = {},
            onSave = {},
            onApply = {},
            onRestoreDefaults = {}
        )
    }
}
