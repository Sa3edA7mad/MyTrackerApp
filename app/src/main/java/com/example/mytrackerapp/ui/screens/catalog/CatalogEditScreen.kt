package com.example.mytrackerapp.ui.screens.catalog

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mytrackerapp.TrackerApplication
import com.example.mytrackerapp.domain.CatalogValidation
import com.example.mytrackerapp.domain.ExerciseDraft
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.ExerciseSlot
import com.example.mytrackerapp.domain.model.TargetType
import com.example.mytrackerapp.repo.CatalogRepository
import com.example.mytrackerapp.ui.Routes
import com.example.mytrackerapp.ui.components.ActionRow
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.ConfirmDialog
import com.example.mytrackerapp.ui.components.LabeledField
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CatalogEditState(
    val id: String? = null,
    val name: String = "",
    val category: Category = Category.BODYWEIGHT,
    val slot: ExerciseSlot = ExerciseSlot.PROGRAM,
    val muscles: String = "",
    val instructions: String = "",
    val targetType: TargetType = TargetType.REPS,
    val targetValue: Int = 5,
    val perSide: Boolean = false,
    val targetLabel: String = "5 reps",
    val progressionStep: Int = 0,
    val tracksReps: Boolean = false,
    val tracksLoad: Boolean = false,
    val defaultLoadKg: String = "",
    val defaultBandLevel: String = "",
    val videoUrl: String = "",
    val enabled: Boolean = true,
    val isArchived: Boolean = false,
    val loaded: Boolean = false
) {
    val isNew: Boolean get() = id == null
}

class CatalogEditViewModel(
    private val catalog: CatalogRepository,
    private val editId: String?
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogEditState(id = editId))
    val state: StateFlow<CatalogEditState> = _state.asStateFlow()

    init {
        if (editId != null) {
            viewModelScope.launch {
                val existing = catalog.observeAll(includeArchived = true).first()
                    .firstOrNull { it.id == editId }
                if (existing != null) {
                    _state.value = existing.toEditState()
                } else {
                    _state.update { it.copy(loaded = true) }
                }
            }
        } else {
            _state.update { it.copy(loaded = true) }
        }
    }

    private fun Exercise.toEditState() = CatalogEditState(
        id = id,
        name = name,
        category = category,
        slot = slot,
        muscles = muscles,
        instructions = instructions,
        targetType = targetType,
        targetValue = targetValue,
        perSide = perSide,
        targetLabel = targetLabel,
        progressionStep = progressionStep,
        tracksReps = tracksReps,
        tracksLoad = tracksLoad,
        defaultLoadKg = defaultLoadKg?.toString() ?: "",
        defaultBandLevel = defaultBandLevel ?: "",
        videoUrl = videoUrl,
        enabled = enabled,
        isArchived = isArchived,
        loaded = true
    )

    private fun MutableStateFlow<CatalogEditState>.update(transform: (CatalogEditState) -> CatalogEditState) {
        value = transform(value)
    }

    fun update(transform: (CatalogEditState) -> CatalogEditState) {
        _state.update(transform)
    }

    private fun currentDraft(): ExerciseDraft {
        val s = _state.value
        return ExerciseDraft(
            name = s.name,
            category = s.category,
            slot = s.slot,
            muscles = s.muscles,
            instructions = s.instructions,
            targetType = s.targetType,
            targetValue = s.targetValue,
            perSide = s.perSide,
            targetLabel = s.targetLabel,
            videoUrl = s.videoUrl,
            tracksReps = s.tracksReps,
            tracksLoad = s.tracksLoad,
            defaultLoadKg = s.defaultLoadKg.toDoubleOrNull(),
            defaultBandLevel = s.defaultBandLevel.ifBlank { null },
            progressionStep = s.progressionStep,
            enabled = s.enabled
        )
    }

    fun validationErrors(): List<String> = CatalogValidation.validate(currentDraft())

    suspend fun save(): Result<String> {
        val errors = validationErrors()
        if (errors.isNotEmpty()) return Result.failure(IllegalArgumentException(errors.first()))
        val draft = currentDraft()
        val id = _state.value.id
        return if (id == null) {
            catalog.create(draft)
        } else {
            catalog.update(id, draft).map { id }
        }
    }

    suspend fun archive(): Result<Unit> {
        val id = _state.value.id ?: return Result.failure(IllegalStateException("Not saved yet."))
        return catalog.archive(id)
    }

    suspend fun restore(): Result<Unit> {
        val id = _state.value.id ?: return Result.failure(IllegalStateException("Not saved yet."))
        return catalog.restore(id)
    }

    companion object {
        fun factory(id: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                CatalogEditViewModel(app.container.catalog, id)
            }
        }
    }
}

@Composable
fun CatalogEditRoute(
    id: String,
    onDone: () -> Unit,
    viewModel: CatalogEditViewModel = viewModel(
        factory = CatalogEditViewModel.factory(id.takeIf { it != Routes.EXERCISE_EDIT_NEW_ID }),
        key = "exerciseEdit/$id"
    )
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }

    if (!state.loaded) return

    CatalogEditScreen(
        state = state,
        snackbars = snackbars,
        errors = viewModel.validationErrors(),
        onBack = onDone,
        onChange = viewModel::update,
        onSave = {
            scope.launch {
                val result = viewModel.save()
                if (result.isSuccess) onDone()
                else scope.launch { snackbars.showSnackbar(result.exceptionOrNull()?.message ?: "Could not save.") }
            }
        },
        onArchive = {
            scope.launch {
                val result = viewModel.archive()
                if (result.isSuccess) onDone()
                else snackbars.showSnackbar(result.exceptionOrNull()?.message ?: "Could not archive.")
            }
        },
        onRestore = {
            scope.launch {
                viewModel.restore()
                onDone()
            }
        }
    )
}

@Composable
fun CatalogEditScreen(
    state: CatalogEditState,
    snackbars: SnackbarHostState,
    errors: List<String>,
    onBack: () -> Unit,
    onChange: ((CatalogEditState) -> CatalogEditState) -> Unit,
    onSave: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit
) {
    var confirmArchive by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = CanvasColor,
        snackbarHost = { SnackbarHost(snackbars) }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
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
                Text(
                    if (state.isNew) "New exercise" else "Edit exercise",
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary
                )
                if (state.isArchived) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text("ARCHIVED", style = MaterialTheme.typography.labelSmall, color = Danger)
                }

                Spacer(Modifier.height(Spacing.md))
                LabeledField("Name", state.name, { onChange { s -> s.copy(name = it) } })

                Spacer(Modifier.height(Spacing.md))
                SectionHeader("Slot")
                ChoiceRow(
                    options = ExerciseSlot.entries.map { it.name },
                    selected = state.slot.ordinal,
                    onSelect = { i -> onChange { it.copy(slot = ExerciseSlot.entries[i]) } }
                )

                SectionHeader("Category")
                ChoiceRow(
                    options = Category.entries.map { it.name },
                    selected = state.category.ordinal,
                    onSelect = { i -> onChange { it.copy(category = Category.entries[i]) } }
                )

                Spacer(Modifier.height(Spacing.md))
                LabeledField("Muscles", state.muscles, { onChange { s -> s.copy(muscles = it) } })
                Spacer(Modifier.height(Spacing.md))
                LabeledField(
                    "Instructions",
                    state.instructions,
                    { onChange { s -> s.copy(instructions = it) } },
                    singleLine = false
                )

                SectionHeader("Target")
                ChoiceRow(
                    options = TargetType.entries.map { it.name },
                    selected = state.targetType.ordinal,
                    onSelect = { i -> onChange { it.copy(targetType = TargetType.entries[i]) } }
                )
                Spacer(Modifier.height(Spacing.sm))
                NumberStepper(
                    "Target value",
                    state.targetValue,
                    1..CatalogValidation.MAX_TARGET_VALUE,
                    { v -> onChange { it.copy(targetValue = v) } }
                )
                SettingRow(
                    "Per side",
                    "Runs as a two-stage left/right flow",
                    state.perSide,
                    { v -> onChange { it.copy(perSide = v) } }
                )
                Spacer(Modifier.height(Spacing.md))
                LabeledField(
                    "Target label",
                    state.targetLabel,
                    { onChange { s -> s.copy(targetLabel = it) } }
                )
                Spacer(Modifier.height(Spacing.sm))
                NumberStepper(
                    "Progression step per week",
                    state.progressionStep,
                    0..CatalogValidation.MAX_PROGRESSION_STEP,
                    { v -> onChange { it.copy(progressionStep = v) } }
                )

                SectionHeader("Tracking")
                SettingRow(
                    "Log reps",
                    "Prompt for reps actually done when you tick it",
                    state.tracksReps,
                    { v -> onChange { it.copy(tracksReps = v) } }
                )
                SettingRow(
                    "Log load",
                    "Prompt for weight or band level",
                    state.tracksLoad,
                    { v -> onChange { it.copy(tracksLoad = v) } }
                )
                if (state.tracksLoad) {
                    Spacer(Modifier.height(Spacing.md))
                    LabeledField(
                        "Default load (kg)",
                        state.defaultLoadKg,
                        { onChange { s -> s.copy(defaultLoadKg = it) } },
                        keyboardType = KeyboardType.Decimal
                    )
                    Spacer(Modifier.height(Spacing.md))
                    LabeledField(
                        "Default band",
                        state.defaultBandLevel,
                        { onChange { s -> s.copy(defaultBandLevel = it) } }
                    )
                }

                Spacer(Modifier.height(Spacing.md))
                LabeledField("Video URL", state.videoUrl, { onChange { s -> s.copy(videoUrl = it) } })

                SectionHeader("Rotation")
                SettingRow(
                    "Enabled",
                    "In the rotation, without archiving",
                    state.enabled,
                    { v -> onChange { it.copy(enabled = v) } }
                )

                if (state.slot == ExerciseSlot.PROGRAM) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        "Changing a program exercise changes the draft's exercises-per-circuit. " +
                            "Applies to the current cycle only after you apply rules.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Accent
                    )
                }

                if (errors.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    errors.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Danger)
                    }
                }

                if (!state.isNew) {
                    Spacer(Modifier.height(Spacing.md))
                    if (state.isArchived) {
                        ActionRow(
                            title = "Restore",
                            subtitle = "Brings it back into the rotation",
                            onClick = onRestore
                        )
                    } else {
                        ActionRow(
                            title = "Archive",
                            subtitle = "Removes it from circuits and the Library. History is kept",
                            tint = Danger,
                            onClick = { confirmArchive = true }
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.xxl))
            }

            StickyCtaBar {
                PrimaryButton(
                    text = "Save",
                    onClick = onSave,
                    enabled = errors.isEmpty()
                )
            }
        }
    }

    if (confirmArchive) {
        ConfirmDialog(
            title = "Archive this exercise?",
            body = "It disappears from circuits and the Library. Its completion history stays " +
                "readable from its detail page, and you can restore it later.",
            confirmLabel = "ARCHIVE",
            onConfirm = { confirmArchive = false; onArchive() },
            onDismiss = { confirmArchive = false }
        )
    }
}

/** A small selectable-chip row shared by the slot/category/target-type pickers on this screen. */
@Composable
private fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .height(MinTouchTarget)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (isSelected) Accent else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) Accent else Outline,
                        RoundedCornerShape(Radius.pill)
                    )
                    .clickable(role = Role.Button, onClick = { onSelect(index) })
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label.replace('_', ' '),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) OnAccent else TextSecondary
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 900)
@Composable
private fun CatalogEditNewPreview() {
    MyTrackerAppTheme {
        CatalogEditScreen(
            state = CatalogEditState(loaded = true),
            snackbars = remember { SnackbarHostState() },
            errors = emptyList(),
            onBack = {},
            onChange = {},
            onSave = {},
            onArchive = {},
            onRestore = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 900)
@Composable
private fun CatalogEditExistingPreview() {
    MyTrackerAppTheme {
        CatalogEditScreen(
            state = CatalogEditState(
                id = "squat",
                name = "Squat",
                category = Category.BODYWEIGHT,
                slot = ExerciseSlot.PROGRAM,
                muscles = "Legs · Glutes · Core",
                instructions = "Feet shoulder-width, toes slightly out.",
                targetValue = 5,
                targetLabel = "5 reps",
                loaded = true
            ),
            snackbars = remember { SnackbarHostState() },
            errors = emptyList(),
            onBack = {},
            onChange = {},
            onSave = {},
            onArchive = {},
            onRestore = {}
        )
    }
}
