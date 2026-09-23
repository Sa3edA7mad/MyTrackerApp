package com.example.mytrackerapp.ui.screens.today

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mytrackerapp.domain.model.CircuitProgress
import com.example.mytrackerapp.domain.model.DayState
import com.example.mytrackerapp.domain.model.TodayView
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.ui.RoutineType
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.CircuitCard
import com.example.mytrackerapp.ui.components.CircuitCardState
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.components.PrimaryButton
import com.example.mytrackerapp.ui.components.ProgressRing
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.StickyCtaBar
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.CatStretch
import com.example.mytrackerapp.ui.theme.CatWarmUp
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.Danger
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.OnAccent
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.OutlineStrong
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary

@Composable
fun TodayRoute(
    onOpenCircuit: (week: Int, day: Int, circuit: Int) -> Unit,
    onOpenRoutine: (RoutineType) -> Unit,
    onOpenSettings: () -> Unit,
    onCycleComplete: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory)
) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is UiState.Loading -> LoadingState()
        is UiState.Error -> ErrorState(s.message)
        is UiState.Ready -> when (val view = s.data) {
            is TodayView.CycleComplete -> CycleFinishedState(onCycleComplete)

            is TodayView.Active -> TodayScreen(
                day = view.day,
                onOpenCircuit = { circuit -> onOpenCircuit(view.day.week, view.day.day, circuit) },
                onOpenRoutine = onOpenRoutine,
                onOpenSettings = onOpenSettings,
                onEndDayEarly = { viewModel.closeDayEarly(view.day.week, view.day.day) }
            )
        }
    }
}

@Composable
fun TodayScreen(
    day: DayState,
    onOpenCircuit: (Int) -> Unit,
    onOpenRoutine: (RoutineType) -> Unit,
    onOpenSettings: () -> Unit,
    onEndDayEarly: () -> Unit
) {
    var confirmEndDay by remember { mutableStateOf(false) }
    val nextCircuit = day.nextCircuit

    Column(Modifier.fillMaxSize()) {

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
        ) {
            Header(day = day, onOpenSettings = onOpenSettings, onEndDayEarly = { confirmEndDay = true })

            Spacer(Modifier.height(Spacing.base))
            Summary(day)

            Spacer(Modifier.height(Spacing.md))
            RoutineCard(
                title = "WARM-UP",
                subtitle = if (day.warmUpDone) "8 moves · done" else "8 moves · before your first circuit",
                accent = CatWarmUp,
                done = day.warmUpDone,
                onClick = { onOpenRoutine(RoutineType.WARMUP) }
            )

            SectionHeader("Circuits")
            day.circuits.forEach { circuit ->
                CircuitCard(
                    index = circuit.index,
                    done = circuit.done,
                    total = circuit.total,
                    state = when {
                        circuit.isComplete -> CircuitCardState.DONE
                        circuit.index == nextCircuit -> CircuitCardState.ACTIVE
                        else -> CircuitCardState.UPCOMING
                    },
                    onClick = { onOpenCircuit(circuit.index) },
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
            }

            Spacer(Modifier.height(Spacing.xs))
            RoutineCard(
                title = "STRETCH",
                subtitle = when {
                    day.stretchDone -> "8 stretches · done"
                    day.allCircuitsComplete -> "8 stretches · finish your day"
                    else -> "8 stretches · after your last circuit"
                },
                accent = CatStretch,
                done = day.stretchDone,
                // Dimmed until the day's circuits are finished, but still reachable.
                dimmed = !day.allCircuitsComplete && !day.stretchDone,
                onClick = { onOpenRoutine(RoutineType.STRETCH) }
            )

            Spacer(Modifier.height(Spacing.lg))
        }

        StickyCtaBar {
            when {
                nextCircuit != null -> PrimaryButton(
                    text = if (!day.warmUpDone) "Warm up, then circuit $nextCircuit"
                    else "Start circuit $nextCircuit",
                    onClick = {
                        if (!day.warmUpDone) onOpenRoutine(RoutineType.WARMUP)
                        else onOpenCircuit(nextCircuit)
                    }
                )

                !day.stretchDone -> PrimaryButton(
                    text = "Finish with stretching",
                    onClick = { onOpenRoutine(RoutineType.STRETCH) }
                )

                else -> PrimaryButton(text = "Day complete", onClick = {}, enabled = false)
            }
        }
    }

    if (confirmEndDay) {
        val undone = day.circuits.filter { !it.isComplete }.map { it.index }
        AlertDialog(
            onDismissRequest = { confirmEndDay = false },
            containerColor = SurfaceColor,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("End day early?") },
            text = {
                Text(
                    if (undone.isEmpty()) {
                        "This day is already complete."
                    } else {
                        "Circuit${if (undone.size > 1) "s" else ""} " +
                            undone.joinToString(", ") + " will stay incomplete, " +
                            "and you'll move on to the next day. Your finished work is kept."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmEndDay = false; onEndDayEarly() }) {
                    Text("END DAY", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEndDay = false }) {
                    Text("KEEP GOING", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun Header(day: DayState, onOpenSettings: () -> Unit, onEndDayEarly: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                "WEEK ${day.week} · DAY ${day.day}",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
            Text("Today", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
            Text(
                "${day.circuitsTotal} circuits · ${day.exercisesPerCircuit} exercises each",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
        IconButton(AppIcons.settings, "Settings", onOpenSettings)
        IconButton(AppIcons.more, "End day early", onEndDayEarly)
    }
}

@Composable
private fun IconButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(MinTouchTarget)
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = TextTertiary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun Summary(day: DayState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.base)
    ) {
        ProgressRing(
            progress = if (day.circuitsTotal == 0) 0f
            else day.circuitsDone.toFloat() / day.circuitsTotal,
            contentDescription = "${day.circuitsDone} of ${day.circuitsTotal} circuits complete"
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${day.circuitsDone}",
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary
                )
                Text(
                    "/ ${day.circuitsTotal}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${day.exercisesDone}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Accent
                )
                Text(
                    " exercises done",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${day.exercisesLeft}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    " left today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun RoutineCard(
    title: String,
    subtitle: String,
    accent: Color,
    done: Boolean,
    onClick: () -> Unit,
    dimmed: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .alpha(if (dimmed) 0.55f else 1f)
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceColor)
            .border(1.dp, Outline, RoundedCornerShape(Radius.md))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(if (done) Accent else Color.Transparent)
                .border(
                    1.5.dp,
                    if (done) Accent else OutlineStrong,
                    RoundedCornerShape(Radius.sm)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Text("✓", style = MaterialTheme.typography.labelSmall, color = OnAccent)
            }
        }
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(accent.copy(alpha = 0.14f))
                    .padding(horizontal = Spacing.sm, vertical = 2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = accent)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
        }
    }
}

/**
 * What Today becomes once all 24 days are settled.
 *
 * An earlier version was a bare button floating in the middle of an empty screen, which
 * gave no sense of having finished anything.
 */
@Composable
private fun CycleFinishedState(onCycleComplete: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "CYCLE COMPLETE",
                style = MaterialTheme.typography.labelSmall,
                color = Accent
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Nothing left to tick",
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                "All 24 days of this cycle are done. Take a look at how it went, then start " +
                    "again whenever you're ready — your history is kept.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
        StickyCtaBar {
            PrimaryButton("See cycle summary", onCycleComplete)
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/* ------------------------------------------------------------------ previews */

private fun previewDay(week: Int = 2, done: List<Int> = listOf(13, 13, 13, 0, 0)) = DayState(
    week = week,
    day = 3,
    circuits = done.mapIndexed { i, d -> CircuitProgress(i + 1, d, total = 13) },
    warmUpDone = true,
    stretchDone = false,
    closed = false,
    exercisesPerCircuit = 13
)

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 880)
@Composable
private fun TodayPreview() {
    MyTrackerAppTheme {
        Box(Modifier.background(CanvasColor)) {
            TodayScreen(previewDay(), {}, {}, {}, {})
        }
    }
}

/** Week 4 has seven circuits — this is the case that pushed the CTA off screen in v1. */
@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 880)
@Composable
private fun TodayWeekFourPreview() {
    MyTrackerAppTheme {
        Box(Modifier.background(CanvasColor)) {
            TodayScreen(previewDay(week = 4, done = listOf(13, 13, 0, 0, 0, 0, 0)), {}, {}, {}, {})
        }
    }
}
