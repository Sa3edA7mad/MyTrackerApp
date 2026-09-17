package com.example.mytrackerapp.ui.screens.measure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.example.mytrackerapp.domain.MetricTrend
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.Units
import com.example.mytrackerapp.domain.model.DerivedBodyStats
import com.example.mytrackerapp.domain.model.Metric
import com.example.mytrackerapp.domain.model.MetricKind
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.repo.MeasurementRepository
import com.example.mytrackerapp.repo.toDisplay
import com.example.mytrackerapp.ui.components.ActionRow
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.components.LabeledField
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.components.PrimaryButton
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.StatTile
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.AccentMuted
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One metric's row on the list screen, values already converted to the display unit. */
data class MetricRow(
    val metric: Metric,
    val unitLabel: String,
    val latest: Double?,
    val deltaFromPrevious: Double?,
    val deltaFromFirst: Double?,
    val points: List<Double>
)

data class MeasureUiState(
    val rows: List<MetricRow>,
    val derived: DerivedBodyStats,
    val units: UnitPrefs
)

private fun Metric.unitLabel(units: UnitPrefs): String = when (kind) {
    MetricKind.WEIGHT -> units.weight.label
    MetricKind.LENGTH -> units.length.label
    MetricKind.PERCENT -> "%"
    MetricKind.COUNT -> ""
}

class MeasureViewModel(
    private val repo: MeasurementRepository,
    unitsFlow: Flow<UnitPrefs>
) : ViewModel() {

    val state: StateFlow<UiState<MeasureUiState>> = combine(
        repo.observeMetrics(),
        repo.observeTrends(),
        repo.observeDerived(),
        unitsFlow
    ) { metrics, trends, derived, units ->
        val trendById = trends.associateBy { it.metricId }
        val rows = metrics.map { metric ->
            val trend = trendById[metric.id] ?: MetricTrend(metric.id, null, null, null, null, null, emptyList())
            MetricRow(
                metric = metric,
                unitLabel = metric.unitLabel(units),
                latest = trend.latest?.let { units.toDisplay(metric.kind, it) },
                deltaFromPrevious = trend.deltaFromPrevious?.let { units.toDisplay(metric.kind, it) },
                deltaFromFirst = trend.deltaFromFirst?.let { units.toDisplay(metric.kind, it) },
                points = trend.points.map { units.toDisplay(metric.kind, it.value) }
            )
        }
        UiState.Ready(MeasureUiState(rows, derived, units)) as UiState<MeasureUiState>
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val loggableMetrics: StateFlow<List<Metric>> = repo.observeMetrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val units: StateFlow<UnitPrefs> = unitsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitPrefs())

    /** Log writes convert display -> canonical per metric, so a bad value in one field
     *  never blocks the rest of the session from saving. */
    suspend fun logSession(entries: Map<String, Double>, takenAt: Long, note: String) {
        entries.forEach { (metricId, value) -> repo.log(metricId, value, takenAt, note) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                MeasureViewModel(app.container.measurements, app.container.rules.observeUnits())
            }
        }
    }
}

@Composable
fun MeasureRoute(
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit,
    onEditMetrics: () -> Unit,
    viewModel: MeasureViewModel = viewModel(factory = MeasureViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()
    val loggable by viewModel.loggableMetrics.collectAsState()
    val units by viewModel.units.collectAsState()
    val scope = rememberCoroutineScope()
    var showLogSheet by remember { mutableStateOf(false) }

    when (val s = state) {
        is UiState.Loading -> LoadingState()
        is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.message, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }
        is UiState.Ready -> {
            MeasureScreen(
                data = s.data,
                onBack = onBack,
                onOpenHistory = onOpenHistory,
                onEditMetrics = onEditMetrics,
                onLog = { showLogSheet = true }
            )
            if (showLogSheet) {
                LogMeasurementsSheet(
                    metrics = loggable,
                    units = units,
                    onSave = { entries, takenAt, note ->
                        scope.launch { viewModel.logSession(entries, takenAt, note) }
                        showLogSheet = false
                    },
                    onDismiss = { showLogSheet = false }
                )
            }
        }
    }
}

@Composable
fun MeasureScreen(
    data: MeasureUiState,
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit,
    onEditMetrics: () -> Unit,
    onLog: () -> Unit
) {
    Scaffold(containerColor = CanvasColor) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                Box(
                    Modifier
                        .size(MinTouchTarget)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onLog),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        painterResource(AppIcons.add),
                        contentDescription = "Log measurements",
                        tint = TextTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text("Body measurements", style = MaterialTheme.typography.displayMedium, color = TextPrimary)

            SectionHeader("Derived")
            DerivedStatsSection(data.derived)

            SectionHeader("Metrics")
            if (data.rows.isEmpty()) {
                Text(
                    "No metrics enabled. Turn some on in Edit metrics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            } else {
                data.rows.forEach { row ->
                    MetricCard(row, onClick = { onOpenHistory(row.metric.id) })
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            Spacer(Modifier.height(Spacing.md))
            ActionRow(
                title = "Edit metrics",
                subtitle = "Enable, disable, or add a custom metric",
                onClick = onEditMetrics
            )

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun DerivedStatsSection(derived: DerivedBodyStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatTile(
            derived.bmi?.let { Units.format(it, 1) } ?: "—",
            "BMI",
            Modifier.weight(1f)
        )
        StatTile(
            derived.waistToHip?.let { Units.format(it, 2) } ?: "—",
            "Waist:hip",
            Modifier.weight(1f)
        )
        StatTile(
            derived.leanMassKg?.let { "${Units.format(it, 1)} kg" } ?: "—",
            "Lean mass",
            Modifier.weight(1f)
        )
    }
    if (derived.bmi == null || derived.waistToHip == null || derived.leanMassKg == null) {
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Log weight, height, waist, hips and body fat to fill these in.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )
    }
}

@Composable
private fun MetricCard(row: MetricRow, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceColor)
            .border(1.dp, Outline, RoundedCornerShape(Radius.md))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.md)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.metric.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                row.latest?.let { "${Units.format(it, row.metric.decimals)} ${row.unitLabel}".trim() } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = Accent
            )
        }
        if (row.deltaFromPrevious != null || row.deltaFromFirst != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                listOfNotNull(
                    row.deltaFromPrevious?.let { "vs last: ${it.signedString(row.metric.decimals)}" },
                    row.deltaFromFirst?.let { "vs first: ${it.signedString(row.metric.decimals)}" }
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        if (row.points.size >= 2) {
            Spacer(Modifier.height(Spacing.sm))
            TrendSparkline(row.points)
        }
    }
}

private fun Double.signedString(decimals: Int): String {
    val formatted = Units.format(kotlin.math.abs(this), decimals)
    return if (this >= 0) "+$formatted" else "-$formatted"
}

@Composable
private fun TrendSparkline(points: List<Double>) {
    val minValue = points.min()
    val maxValue = points.max()
    val range = (maxValue - minValue).let { if (it == 0.0) 1.0 else it }
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                contentDescription = "Latest ${Units.format(points.last(), 1)}, " +
                    if (points.last() >= points.first()) "trending up" else "trending down"
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEach { value ->
            val fraction = ((value - minValue) / range).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction.coerceAtLeast(0.08f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentMuted)
            )
        }
    }
}

/* ------------------------------------------------------------------ log sheet */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogMeasurementsSheet(
    metrics: List<Metric>,
    units: UnitPrefs,
    onSave: (Map<String, Double>, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { LocalDate.now().toString() }
    var dateText by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }
    val values = remember { mutableStateOf(mapOf<String, String>()) }
    val sheetState = rememberModalBottomSheetState()

    fun takenAtMillis(): Long =
        runCatching { LocalDate.parse(dateText).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            .getOrElse { System.currentTimeMillis() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceColor
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Text("Log a measuring session", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(Spacing.md))
            LabeledField(
                label = "Date (yyyy-mm-dd)",
                value = dateText,
                onValueChange = { dateText = it }
            )
            Spacer(Modifier.height(Spacing.md))

            metrics.forEach { metric ->
                LabeledField(
                    label = "${metric.name} (${metric.unitLabel(units)})".trim(),
                    value = values.value[metric.id].orEmpty(),
                    onValueChange = { v -> values.value = values.value + (metric.id to v) },
                    keyboardType = KeyboardType.Decimal
                )
                Spacer(Modifier.height(Spacing.md))
            }

            LabeledField(label = "Note", value = note, onValueChange = { note = it })
            Spacer(Modifier.height(Spacing.lg))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                GhostButton(
                    "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    "Save",
                    onClick = {
                        val entries = values.value.mapNotNull { (id, text) ->
                            text.toDoubleOrNull()?.let { id to it }
                        }.toMap()
                        if (entries.isNotEmpty()) onSave(entries, takenAtMillis(), note)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 900)
@Composable
private fun MeasureScreenPreview() {
    val today = Instant.now().toEpochMilli()
    val weight = Metric("bodyweight", "Body weight", MetricKind.WEIGHT, "hint", true, false, 1, 1)
    val waist = Metric("waist", "Waist", MetricKind.LENGTH, "hint", true, false, 1, 2)
    MyTrackerAppTheme {
        MeasureScreen(
            data = MeasureUiState(
                rows = listOf(
                    MetricRow(weight, "kg", 82.3, -0.4, -3.1, listOf(85.4, 84.1, 83.0, 82.3)),
                    MetricRow(waist, "cm", 88.0, 0.0, -2.0, listOf(90.0, 89.0, 88.0))
                ),
                derived = DerivedBodyStats(24.7, 0.87, null, 64.0),
                units = UnitPrefs()
            ),
            onBack = {},
            onOpenHistory = {},
            onEditMetrics = {},
            onLog = {}
        )
    }
}
