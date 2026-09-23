package com.example.mytrackerapp.ui.screens.exercise

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mytrackerapp.TrackerApplication
import com.example.mytrackerapp.domain.PerformanceSummary
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.DayTally
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.ExerciseDetail
import com.example.mytrackerapp.domain.model.TargetType
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.repo.TrackerRepository
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.CategoryChip
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.StatTile
import com.example.mytrackerapp.ui.openVideo
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.AccentMuted
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.Danger
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.SurfaceHigh
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ExerciseDetailViewModel(repo: TrackerRepository, id: String) : ViewModel() {

    val state: StateFlow<UiState<ExerciseDetail>> = repo.observeExerciseDetail(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val performance: StateFlow<PerformanceSummary?> =
        repo.observePerformance(id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(id: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                ExerciseDetailViewModel(app.container.repo, id)
            }
        }
    }
}

@Composable
fun ExerciseDetailRoute(id: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val viewModel: ExerciseDetailViewModel = viewModel(
        factory = ExerciseDetailViewModel.factory(id),
        key = "exercise/$id"
    )
    val state by viewModel.state.collectAsState()
    val performance by viewModel.performance.collectAsState()

    when (val s = state) {
        is UiState.Loading -> LoadingState()
        is UiState.Error -> Box(
            Modifier.fillMaxSize().padding(Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                s.message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        is UiState.Ready -> ExerciseDetailScreen(
            detail = s.data,
            performance = performance,
            onBack = onBack,
            onEdit = { onEdit(id) }
        )
    }
}

@Composable
fun ExerciseDetailScreen(
    detail: ExerciseDetail,
    performance: PerformanceSummary? = null,
    onBack: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val exercise = detail.exercise
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = CanvasColor,
        snackbarHost = { SnackbarHost(snackbars) }
    ) { inner ->
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
                        .clickable(role = Role.Button, onClick = onEdit),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        painterResource(AppIcons.edit),
                        contentDescription = "Edit exercise",
                        tint = TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (exercise.isArchived) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "ARCHIVED — kept for history, no longer in the rotation",
                    style = MaterialTheme.typography.labelSmall,
                    color = Danger
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            CategoryChip(exercise.category)
            Spacer(Modifier.height(Spacing.md))
            Text(
                exercise.name,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            if (exercise.muscles.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    exercise.muscles,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }

            Spacer(Modifier.height(Spacing.base))
            TargetPlaque(exercise)

            SectionHeader("How to")
            Text(
                exercise.instructions,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(Modifier.height(Spacing.base))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceColor)
                    .border(1.dp, Outline, RoundedCornerShape(Radius.md))
                    .clickable(role = Role.Button) {
                        if (!openVideo(context, exercise.videoUrl)) {
                            scope.launch {
                                snackbars.showSnackbar("No app can open ${exercise.videoUrl}")
                            }
                        }
                    }
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    painterResource(AppIcons.play),
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Watch form video",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text("↗", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
            }

            SectionHeader("Your history")
            if (detail.hasHistory) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${detail.totalThisCycle}",
                        style = MaterialTheme.typography.displayMedium,
                        color = Accent
                    )
                    Text(
                        " completions this cycle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Sparkline(detail.recent, detail.maxCount)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "LAST ${detail.recent.size} TRAINING DAY${if (detail.recent.size == 1) "" else "S"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            } else {
                Text(
                    "Not done yet this cycle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }

            if (performance != null && performance.loggedSets > 0) {
                SectionHeader("Performance")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatTile(
                        performance.bestLoadKg?.let { "%.1f kg".format(it) }
                            ?: performance.bestReps?.let { "$it" }
                            ?: performance.bestHoldSeconds?.let { "${it}s" }
                            ?: "—",
                        "Best",
                        Modifier.weight(1f)
                    )
                    StatTile(
                        performance.bestReps?.let { "$it" } ?: "—",
                        "Best reps",
                        Modifier.weight(1f)
                    )
                    StatTile(
                        performance.totalVolumeKg?.let { "%.0f kg".format(it) } ?: "—",
                        "Total volume",
                        Modifier.weight(1f)
                    )
                }
                if (performance.volumeByDay.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.md))
                    val maxVolume = performance.volumeByDay.maxOf { it.volumeKg }
                    Row(
                        Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        performance.volumeByDay.forEach { day ->
                            val fraction = if (maxVolume == 0.0) 0f else (day.volumeKg / maxVolume).toFloat()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(fraction.coerceAtLeast(0.06f))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Accent)
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "VOLUME BY TRAINING DAY",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            } else if (detail.hasHistory) {
                SectionHeader("Performance")
                Text(
                    "No sets logged yet. Turn on rep or load logging in the exercise editor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun TargetPlaque(exercise: Exercise) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceHigh)
            .border(1.dp, Outline, RoundedCornerShape(Radius.md))
            .padding(horizontal = Spacing.base, vertical = Spacing.md),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            "${exercise.targetValue}",
            style = MaterialTheme.typography.displayMedium,
            color = Accent
        )
        Text(
            if (exercise.targetType == TargetType.REPS) "REPS" else "SEC",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        if (exercise.perSide) {
            Text(
                "EACH SIDE",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun Sparkline(tallies: List<DayTally>, maxCount: Int) {
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Bottom
    ) {
        tallies.forEach { tally ->
            val fraction = if (maxCount == 0) 0f else tally.count.toFloat() / maxCount
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction.coerceAtLeast(0.06f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (fraction > 0.66f) Accent else AccentMuted)
            )
        }
    }
}

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 880)
@Composable
private fun ExerciseDetailPreview() {
    val today = LocalDate.of(2026, 9, 16)
    MyTrackerAppTheme {
        ExerciseDetailScreen(
            detail = ExerciseDetail(
                exercise = Exercise(
                    id = "band_row",
                    name = "Band Row",
                    category = Category.BAND,
                    muscles = "Back · Biceps · Rear Delts",
                    instructions = "Anchor band at waist height. Hold with both hands, step back for tension. Pull hands toward torso squeezing shoulder blades. Return slowly.",
                    targetType = TargetType.REPS,
                    targetValue = 10,
                    perSide = false,
                    targetLabel = "10 reps",
                    videoUrl = "https://www.youtube.com/watch?v=LSkyinhmA8k",
                    sortOrder = 13
                ),
                totalThisCycle = 47,
                recent = (0..6).map { DayTally(today.minusDays((6 - it).toLong()), 2 + it % 5) }
            ),
            onBack = {}
        )
    }
}
