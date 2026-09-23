package com.example.mytrackerapp.ui.screens.measure

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.Units
import com.example.mytrackerapp.domain.model.Metric
import com.example.mytrackerapp.domain.model.MetricKind
import com.example.mytrackerapp.domain.model.MeasurementEntry
import com.example.mytrackerapp.repo.MeasurementRepository
import com.example.mytrackerapp.repo.toDisplay
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.ConfirmDialog
import com.example.mytrackerapp.ui.components.LabeledField
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.Danger
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MetricHistoryState(
    val metric: Metric?,
    val units: UnitPrefs,
    val entries: List<MeasurementEntry>
)

class MetricHistoryViewModel(
    private val repo: MeasurementRepository,
    private val metricId: String,
    unitsFlow: Flow<UnitPrefs>
) : ViewModel() {

    val state: StateFlow<MetricHistoryState> = combine(
        repo.observeMetrics(includeDisabled = true).map { list -> list.firstOrNull { it.id == metricId } },
        unitsFlow,
        repo.observeHistory(metricId)
    ) { metric, units, entries ->
        MetricHistoryState(metric, units, entries.sortedByDescending { it.takenAt })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetricHistoryState(null, UnitPrefs(), emptyList()))

    suspend fun update(id: Long, displayValue: Double, note: String): Result<Unit> =
        repo.updateEntry(id, displayValue, note, metricId)

    suspend fun delete(id: Long): Result<Unit> = repo.deleteEntry(id)

    companion object {
        fun factory(metricId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                MetricHistoryViewModel(
                    app.container.measurements,
                    metricId,
                    app.container.rules.observeUnits()
                )
            }
        }
    }
}

private fun Metric.unit(units: UnitPrefs): String = when (kind) {
    MetricKind.WEIGHT -> units.weight.label
    MetricKind.LENGTH -> units.length.label
    MetricKind.PERCENT -> "%"
    MetricKind.COUNT -> ""
}

@Composable
fun MetricHistoryRoute(
    metricId: String,
    onBack: () -> Unit,
    viewModel: MetricHistoryViewModel = viewModel(
        factory = MetricHistoryViewModel.factory(metricId),
        key = "metricHistory/$metricId"
    )
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val metric = state.metric

    if (metric == null) {
        LoadingState()
        return
    }

    MetricHistoryScreen(
        metric = metric,
        units = state.units,
        entries = state.entries,
        onBack = onBack,
        onUpdate = { id, value, note ->
            scope.launch { viewModel.update(id, value, note) }
        },
        onDelete = { id ->
            scope.launch { viewModel.delete(id) }
        }
    )
}

@Composable
fun MetricHistoryScreen(
    metric: Metric,
    units: UnitPrefs,
    entries: List<MeasurementEntry>,
    onBack: () -> Unit,
    onUpdate: (Long, Double, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var editing by remember { mutableStateOf<MeasurementEntry?>(null) }
    var deleting by remember { mutableStateOf<MeasurementEntry?>(null) }
    val unitLabel = metric.unit(units)

    Column(Modifier.fillMaxSize().background(CanvasColor).padding(horizontal = Spacing.lg)) {
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
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
        }
        Text(metric.name, style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Spacer(Modifier.height(Spacing.md))

        if (entries.isEmpty()) {
            Text(
                "No readings yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        displayValue = units.toDisplay(metric.kind, entry.value),
                        unitLabel = unitLabel,
                        decimals = metric.decimals,
                        onEdit = { editing = entry },
                        onDelete = { deleting = entry }
                    )
                }
                item { Spacer(Modifier.height(Spacing.xxl)) }
            }
        }
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            initialDisplayValue = units.toDisplay(metric.kind, entry.value),
            decimals = metric.decimals,
            unitLabel = unitLabel,
            onSave = { value, note ->
                onUpdate(entry.id, value, note)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { entry ->
        ConfirmDialog(
            title = "Delete this reading?",
            body = "This is the one hard delete in the app — a typo'd reading has no historical value, but a real one is gone for good.",
            confirmLabel = "DELETE",
            onConfirm = { onDelete(entry.id); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun HistoryRow(
    entry: MeasurementEntry,
    displayValue: Double,
    unitLabel: String,
    decimals: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val date = remember(entry.takenAt) {
        Instant.ofEpochMilli(entry.takenAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceColor)
            .border(1.dp, Outline, RoundedCornerShape(Radius.md))
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${Units.format(displayValue, decimals)} $unitLabel".trim(),
                style = MaterialTheme.typography.titleMedium,
                color = Accent
            )
            Text(date, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            if (entry.note.isNotBlank()) {
                Text(entry.note, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onEdit),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(AppIcons.edit),
                contentDescription = "Edit reading",
                tint = TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(AppIcons.trash),
                contentDescription = "Delete reading",
                tint = Danger,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EditEntryDialog(
    entry: MeasurementEntry,
    initialDisplayValue: Double,
    decimals: Int,
    unitLabel: String,
    onSave: (Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(Units.format(initialDisplayValue, decimals)) }
    var note by remember { mutableStateOf(entry.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        titleContentColor = TextPrimary,
        title = { Text("Edit reading") },
        text = {
            Column {
                LabeledField(
                    label = "Value ($unitLabel)".trim(),
                    value = value,
                    onValueChange = { value = it.filter { c -> c.isDigit() || c == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                Spacer(Modifier.height(Spacing.sm))
                LabeledField(label = "Note", value = note, onValueChange = { note = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                value.toDoubleOrNull()?.let { onSave(it, note) }
            }) { Text("SAVE", color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = TextSecondary) }
        }
    )
}

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 880)
@Composable
private fun MetricHistoryPreview() {
    val now = System.currentTimeMillis()
    MyTrackerAppTheme {
        MetricHistoryScreen(
            metric = Metric("bodyweight", "Body weight", MetricKind.WEIGHT, "hint", true, false, 1, 1),
            units = UnitPrefs(),
            entries = listOf(
                MeasurementEntry(3, "bodyweight", 82.3, now, "After breakfast"),
                MeasurementEntry(2, "bodyweight", 83.0, now - 86_400_000L, ""),
                MeasurementEntry(1, "bodyweight", 84.1, now - 2 * 86_400_000L, "")
            ),
            onBack = {},
            onUpdate = { _, _, _ -> },
            onDelete = {}
        )
    }
}
