package com.example.mytrackerapp.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
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
import com.example.mytrackerapp.data.prefs.Settings
import com.example.mytrackerapp.data.prefs.SettingsStore
import com.example.mytrackerapp.repo.TrackerRepository
import com.example.mytrackerapp.ui.components.ActionRow
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.ConfirmDialog
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.SettingRow
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.Danger
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.compose.ui.graphics.Color
import com.example.mytrackerapp.ui.theme.glassBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.mytrackerapp.data.prefs.ThemeMode
import com.example.mytrackerapp.ui.theme.TrackerPalette
import com.example.mytrackerapp.ui.theme.CharcoalPalette
import com.example.mytrackerapp.ui.theme.SteelPalette
import com.example.mytrackerapp.ui.theme.FrostPalette
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.OutlineStrong
import com.example.mytrackerapp.ui.theme.Accent

class SettingsViewModel(
    private val repo: TrackerRepository,
    private val store: SettingsStore
) : ViewModel() {

    val settings: StateFlow<Settings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setGuidedMode(v: Boolean) = viewModelScope.launch { store.setGuidedMode(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { store.setHaptics(v) }
    fun setSoundCues(v: Boolean) = viewModelScope.launch { store.setSoundCues(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { store.setKeepScreenOn(v) }
    fun setAutoAdvance(v: Boolean) = viewModelScope.launch { store.setAutoAdvanceTimer(v) }
    fun setThemeMode(v: ThemeMode) = viewModelScope.launch { store.setThemeMode(v) }

    fun resetCycle() = viewModelScope.launch { repo.resetActiveCycle() }

    /** Writes the export to a document the user picked. */
    fun export(context: Context, uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = repo.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray())
                    } ?: error("could not open $uri")
                }
            }.isSuccess
            onResult(ok)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                SettingsViewModel(app.container.repo, app.container.settings)
            }
        }
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenMeasure: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.export(context, uri) { ok ->
            scope.launch {
                snackbars.showSnackbar(
                    if (ok) "Training data exported." else "Export failed."
                )
            }
        }
    }

    SettingsScreen(
        settings = settings,
        snackbars = snackbars,
        onBack = onBack,
        onOpenRules = onOpenRules,
        onOpenMeasure = onOpenMeasure,
        onGuided = viewModel::setGuidedMode,
        onHaptics = viewModel::setHaptics,
        onSound = viewModel::setSoundCues,
        onKeepAwake = viewModel::setKeepScreenOn,
        onAutoAdvance = viewModel::setAutoAdvance,
        onThemeMode = viewModel::setThemeMode,
        onExport = { exportLauncher.launch("mytracker-export-${LocalDate.now()}.json") },
        onResetCycle = { viewModel.resetCycle() }
    )
}

@Composable
fun SettingsScreen(
    settings: Settings,
    snackbars: SnackbarHostState,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenMeasure: () -> Unit,
    onGuided: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onKeepAwake: (Boolean) -> Unit,
    onAutoAdvance: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onExport: () -> Unit,
    onResetCycle: () -> Unit
) {
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.glassBackdrop(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbars) }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
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
            Text("Settings", style = MaterialTheme.typography.displayMedium, color = TextPrimary)

            SectionHeader("Appearance")
            Column(Modifier.selectableGroup()) {
                themeOptions.forEach { option ->
                    ThemeOptionRow(
                        option,
                        selected = settings.themeMode == option.mode,
                        onClick = { onThemeMode(option.mode) }
                    )
                }
            }

            SectionHeader("Program")
            ActionRow(
                title = "Program rules",
                subtitle = "Weeks, days, circuits, warm-up/stretch, counting and locking",
                onClick = onOpenRules
            )

            SectionHeader("Health")
            ActionRow(
                title = "Body measurements",
                subtitle = "Weight, girths, resting heart rate and derived stats",
                onClick = onOpenMeasure
            )

            SectionHeader("Session")
            SettingRow(
                title = "Guided mode by default",
                subtitle = "One exercise at a time instead of a flat checklist",
                checked = settings.guidedMode,
                onCheckedChange = onGuided
            )
            SettingRow(
                title = "Timer auto-advance",
                subtitle = "Move on automatically when a hold reaches zero",
                checked = settings.autoAdvanceTimer,
                onCheckedChange = onAutoAdvance
            )
            SettingRow(
                title = "Keep screen awake",
                subtitle = "Stops the display sleeping mid-circuit",
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepAwake
            )

            SectionHeader("Feedback")
            SettingRow(
                title = "Sound cues",
                subtitle = "Beeps at 3, 2, 1 and at zero. The only cue you can hear with the phone on the floor",
                checked = settings.soundCues,
                onCheckedChange = onSound
            )
            SettingRow(
                title = "Haptics",
                subtitle = "Vibrate when you tick an exercise",
                checked = settings.haptics,
                onCheckedChange = onHaptics
            )

            SectionHeader("Your data")
            ActionRow(
                title = "Export training data",
                subtitle = "Writes every cycle to a JSON file you choose",
                onClick = onExport
            )
            Spacer(Modifier.height(Spacing.sm))
            ActionRow(
                title = "Reset current cycle",
                subtitle = "Clears this cycle's completions. Earlier cycles are kept",
                tint = Danger,
                onClick = { confirmReset = true }
            )

            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset this cycle?",
            body = "Every completion in the current cycle is deleted and you go back to " +
                "Week 1 Day 1. Completed earlier cycles are not touched. " +
                "This cannot be undone — export first if you want a copy.",
            confirmLabel = "RESET CYCLE",
            onConfirm = { confirmReset = false; onResetCycle() },
            onDismiss = { confirmReset = false }
        )
    }
}

private data class ThemeOption(
    val mode: ThemeMode,
    val title: String,
    val subtitle: String,
    val left: TrackerPalette,
    val right: TrackerPalette
)

private val themeOptions = listOf(
    ThemeOption(ThemeMode.SYSTEM, "Match device", "Light charcoal in dark mode, pale frost in light mode", CharcoalPalette, FrostPalette),
    ThemeOption(ThemeMode.CHARCOAL, "Light charcoal", "Soft dark gray with the lime accent", CharcoalPalette, CharcoalPalette),
    ThemeOption(ThemeMode.STEEL, "Steel glass", "Mid-gray glass with dark text", SteelPalette, SteelPalette),
    ThemeOption(ThemeMode.FROST, "Pale frost", "Light frosted glass with dark text", FrostPalette, FrostPalette)
)

@Composable
private fun ThemeOptionRow(option: ThemeOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(Radius.md))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Swatch: each half is that palette's canvas; the dot is the right-hand palette's accent.
        Box(
            Modifier.size(32.dp).clip(CircleShape).border(1.dp, Outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().background(option.left.canvas))
                Box(Modifier.weight(1f).fillMaxHeight().background(option.right.canvas))
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(option.right.accent))
        }
        Column(Modifier.weight(1f)) {
            Text(option.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(option.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .border(2.dp, if (selected) Accent else OutlineStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Accent))
        }
    }
}

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF4A5052, widthDp = 400, heightDp = 880)
@Composable
private fun SettingsPreview() {
    MyTrackerAppTheme {
        SettingsScreen(
            settings = Settings(),
            snackbars = remember { SnackbarHostState() },
            onBack = {}, onOpenRules = {}, onOpenMeasure = {}, onGuided = {}, onHaptics = {}, onSound = {},
            onKeepAwake = {}, onAutoAdvance = {}, onThemeMode = {}, onExport = {}, onResetCycle = {}
        )
    }
}
