package com.example.mytrackerapp.ui.screens.measure

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.example.mytrackerapp.domain.model.Metric
import com.example.mytrackerapp.domain.model.MetricKind
import com.example.mytrackerapp.repo.MeasurementRepository
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.ConfirmDialog
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.components.LabeledField
import com.example.mytrackerapp.ui.components.NumberStepper
import com.example.mytrackerapp.ui.components.PrimaryButton
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.SettingRow
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.Danger
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MetricCatalogViewModel(private val repo: MeasurementRepository) : ViewModel() {

    val metrics: StateFlow<List<Metric>> = repo.observeMetrics(includeDisabled = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repo.setMetricEnabled(id, enabled) }
    }

    suspend fun archive(id: String): Result<Unit> = repo.archiveMetric(id)

    suspend fun createCustom(name: String, kind: MetricKind, decimals: Int, hint: String): Result<String> =
        repo.createMetric(name, kind, decimals, hint)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                MetricCatalogViewModel(app.container.measurements)
            }
        }
    }
}

@Composable
fun MetricCatalogRoute(
    onBack: () -> Unit,
    viewModel: MetricCatalogViewModel = viewModel(factory = MetricCatalogViewModel.Factory)
) {
    val metrics by viewModel.metrics.collectAsState()
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf<Metric?>(null) }

    MetricCatalogScreen(
        metrics = metrics,
        onBack = onBack,
        onToggle = viewModel::setEnabled,
        onArchive = { confirmArchive = it },
        onAddCustom = { showCreate = true }
    )

    if (showCreate) {
        CreateMetricSheet(
            onCreate = { name, kind, decimals, hint ->
                scope.launch { viewModel.createCustom(name, kind, decimals, hint) }
                showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }

    confirmArchive?.let { metric ->
        ConfirmDialog(
            title = "Archive ${metric.name}?",
            body = "It disappears from the measurements screen. Its readings are kept.",
            confirmLabel = "ARCHIVE",
            onConfirm = {
                scope.launch { viewModel.archive(metric.id) }
                confirmArchive = null
            },
            onDismiss = { confirmArchive = null }
        )
    }
}

@Composable
fun MetricCatalogScreen(
    metrics: List<Metric>,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onArchive: (Metric) -> Unit,
    onAddCustom: () -> Unit
) {
    val active = metrics.filter { !it.isArchived }.sortedBy { it.sortOrder }
    val archived = metrics.filter { it.isArchived }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg)
            .verticalScroll(rememberScrollState())
    ) {
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
        Text("Edit metrics", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
        Spacer(Modifier.height(Spacing.md))

        active.forEach { metric ->
            SettingRow(
                title = metric.name,
                subtitle = metric.hint,
                checked = metric.enabled,
                onCheckedChange = { onToggle(metric.id, it) }
            )
            if (metric.isCustom) {
                Row(Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
                    GhostButton(
                        "Archive",
                        onClick = { onArchive(metric) },
                        tint = Danger
                    )
                }
            }
        }

        if (archived.isNotEmpty()) {
            SectionHeader("Archived")
            archived.forEach { metric ->
                Text(
                    metric.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    modifier = Modifier.padding(vertical = Spacing.sm)
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
        GhostButton("+ Add custom metric", onClick = onAddCustom, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateMetricSheet(
    onCreate: (String, MetricKind, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(MetricKind.LENGTH) }
    var decimals by remember { mutableStateOf(1) }
    var hint by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SurfaceColor) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Text("New metric", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(Spacing.md))
            LabeledField(label = "Name", value = name, onValueChange = { name = it })
            Spacer(Modifier.height(Spacing.md))

            Text("Kind", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                MetricKind.entries.forEach { k ->
                    GhostButton(
                        k.name,
                        onClick = { kind = k },
                        tint = if (kind == k) TextPrimary else TextTertiary,
                        modifier = Modifier.padding(end = Spacing.xs)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))

            NumberStepper("Decimals", decimals, 0..2, { decimals = it })
            Spacer(Modifier.height(Spacing.md))
            LabeledField(
                label = "Hint",
                value = hint,
                onValueChange = { hint = it },
                keyboardType = KeyboardType.Text
            )
            Spacer(Modifier.height(Spacing.lg))

            PrimaryButton(
                "Add metric",
                onClick = { if (name.isNotBlank()) onCreate(name, kind, decimals, hint) },
                enabled = name.isNotBlank()
            )
        }
    }
}

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 900)
@Composable
private fun MetricCatalogPreview() {
    MyTrackerAppTheme {
        MetricCatalogScreen(
            metrics = listOf(
                Metric("bodyweight", "Body weight", MetricKind.WEIGHT, "Same time of day", true, false, 1, 1),
                Metric("waist", "Waist", MetricKind.LENGTH, "At the navel", true, false, 1, 2),
                Metric("neck", "Neck", MetricKind.LENGTH, "Below the Adam's apple", false, false, 1, 3)
            ),
            onBack = {},
            onToggle = { _, _ -> },
            onArchive = {},
            onAddCustom = {}
        )
    }
}
