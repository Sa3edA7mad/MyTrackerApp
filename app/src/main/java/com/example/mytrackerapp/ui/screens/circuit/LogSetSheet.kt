package com.example.mytrackerapp.ui.screens.circuit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.Units
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.targetForWeek
import com.example.mytrackerapp.repo.SetDetail
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.components.LabeledField
import com.example.mytrackerapp.ui.components.PrimaryButton
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.OnAccent
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.SurfaceHigh
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextTertiary

private fun formatLoadValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

/**
 * Opened instead of an immediate tick when an exercise tracks reps and/or load. SKIP, and
 * dismissing the sheet without saving, both finish the set with no detail — logging must
 * never block finishing a circuit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSetSheet(
    exercise: Exercise,
    week: Int,
    unitPrefs: UnitPrefs,
    loadLastDetail: suspend () -> SetDetail?,
    onSave: (SetDetail) -> Unit,
    onSkip: () -> Unit
) {
    var reps by remember(exercise.id) { mutableStateOf("") }
    var load by remember(exercise.id) { mutableStateOf("") }
    var band by remember(exercise.id) { mutableStateOf(exercise.defaultBandLevel.orEmpty()) }
    var rpe by remember(exercise.id) { mutableStateOf<Int?>(null) }
    var note by remember(exercise.id) { mutableStateOf("") }

    LaunchedEffect(exercise.id) {
        val last = loadLastDetail()
        if (exercise.tracksReps) {
            reps = (last?.reps ?: exercise.targetForWeek(week)).toString()
        }
        if (exercise.tracksLoad) {
            val loadKg = last?.loadKg ?: exercise.defaultLoadKg
            if (loadKg != null) {
                load = formatLoadValue(Units.kgToDisplay(loadKg, unitPrefs.weight))
            }
        }
        band = last?.bandLevel ?: exercise.defaultBandLevel.orEmpty()
        rpe = last?.rpe
        note = last?.note.orEmpty()
    }

    val sheetState = rememberModalBottomSheetState()

    fun buildDetail(): SetDetail = SetDetail(
        reps = if (exercise.tracksReps) reps.toIntOrNull() else null,
        loadKg = if (exercise.tracksLoad) {
            load.toDoubleOrNull()?.let { Units.displayToKg(it, unitPrefs.weight) }
        } else null,
        bandLevel = band.trim().ifBlank { null },
        rpe = rpe,
        note = note.trim().ifBlank { null }
    )

    ModalBottomSheet(
        onDismissRequest = onSkip,
        sheetState = sheetState,
        containerColor = SurfaceColor
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(Spacing.lg))

            if (exercise.tracksReps) {
                LabeledField(
                    label = "Reps",
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit) },
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(Spacing.md))
            }
            if (exercise.tracksLoad) {
                LabeledField(
                    label = "Load (${unitPrefs.weight.label})",
                    value = load,
                    onValueChange = { new -> load = new.filter { it.isDigit() || it == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                Spacer(Modifier.height(Spacing.md))
            }
            if (exercise.defaultBandLevel != null) {
                LabeledField(
                    label = "Band",
                    value = band,
                    onValueChange = { band = it }
                )
                Spacer(Modifier.height(Spacing.md))
            }

            Text("RPE", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                (1..10).forEach { value ->
                    RpeChip(
                        value = value,
                        selected = rpe == value,
                        onClick = { rpe = if (rpe == value) null else value }
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))

            LabeledField(label = "Note", value = note, onValueChange = { note = it })
            Spacer(Modifier.height(Spacing.lg))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GhostButton("Skip", onClick = onSkip, modifier = Modifier.weight(1f))
                PrimaryButton(
                    "Save",
                    onClick = { onSave(buildDetail()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RpeChip(value: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (selected) Accent else SurfaceHigh)
            .border(1.dp, if (selected) Accent else Outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) OnAccent else TextPrimary
        )
    }
}
