package com.example.mytrackerapp.ui.screens.circuit

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.CircuitView
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.formVideoUrl
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.CheckMark
import com.example.mytrackerapp.ui.components.ConfirmDialog
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.components.PrimaryButton
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.StickyCtaBar
import com.example.mytrackerapp.ui.components.TargetBadge
import com.example.mytrackerapp.ui.components.accent
import com.example.mytrackerapp.ui.components.label
import com.example.mytrackerapp.ui.openVideo
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.SurfaceHigh
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextTertiary

/**
 * Flat list of the whole circuit (or warm-up / stretch routine).
 *
 * Each row has three tap zones: the checkbox ticks it, the name opens that one exercise in
 * the guided view ([onOpenExercise] — timer, per-side flow, rep/load sheet), and the play
 * button opens its form video. Rows stay tickable on a locked future day: list view is the
 * deliberate escape hatch from INVARIANT 4, and a note says which day the ticks land on.
 *
 * This is a LazyColumn and it scrolls: 13 rows at the 48dp minimum tap target is roughly
 * 624dp of content before headers, so it cannot fit on one screen. The mockup drew
 * shorter rows; the tap-target spec wins.
 */
@Composable
fun ChecklistMode(
    view: CircuitView,
    title: String,
    hapticsEnabled: Boolean = true,
    onToggle: (String, Boolean) -> Unit,
    onExit: () -> Unit,
    onSwitchToGuided: (() -> Unit)?,
    onOpenExercise: (String) -> Unit,
    /** Ticks every exercise that is not done yet. Skips the rep/load sheet. */
    onCompleteAll: () -> Unit,
    /** Routines only, e.g. "Done with warm-up" — sets the day flag and leaves. */
    finishAction: Pair<String, () -> Unit>? = null
) {
    val grouped = view.exercises.groupBy { it.category }
    val remaining = view.total - view.done
    var confirmCompleteAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.sm, end = Spacing.lg, top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(MinTouchTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onExit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(AppIcons.close),
                    contentDescription = "Close circuit",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.weight(1f)
            )
            if (onSwitchToGuided != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable(role = Role.Button, onClick = onSwitchToGuided)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
                ) {
                    Text(
                        "⇄ GUIDED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = Spacing.lg)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${view.done}",
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary
                )
                Text(
                    "/${view.total}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceHigh)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(
                            if (view.total == 0) 0f else view.done.toFloat() / view.total
                        )
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Accent)
                )
            }
        }

        if (!view.editable) {
            Text(
                "Future day — ticks here count for week ${view.week} day ${view.day}.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            )
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            listOf(
                Category.BODYWEIGHT, Category.BAND, Category.WARMUP, Category.STRETCH
            ).forEach { category ->
                val list = grouped[category].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "header-${category.name}") {
                        SectionHeader(
                            "${category.label} · ${list.size}",
                            color = category.accent
                        )
                    }
                    items(list, key = { it.id }) { exercise ->
                        ChecklistRow(
                            exercise = exercise,
                            done = exercise.id in view.doneIds,
                            hapticsEnabled = hapticsEnabled,
                            onToggle = { checked -> onToggle(exercise.id, checked) },
                            onOpen = { onOpenExercise(exercise.id) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }

        if (remaining > 0 || finishAction != null) {
            StickyCtaBar {
                if (remaining > 0) {
                    GhostButton(
                        "✓ Complete all ($remaining left)",
                        onClick = { confirmCompleteAll = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (finishAction != null) {
                    PrimaryButton(finishAction.first, finishAction.second)
                }
            }
        }
    }

    if (confirmCompleteAll) {
        ConfirmDialog(
            title = "Complete all?",
            body = "Mark the $remaining remaining exercise${if (remaining == 1) "" else "s"} " +
                "done. Rep/load logging is skipped for these.",
            confirmLabel = "COMPLETE ALL",
            onConfirm = {
                confirmCompleteAll = false
                onCompleteAll()
            },
            onDismiss = { confirmCompleteAll = false },
            destructive = false
        )
    }
}

@Composable
private fun ChecklistRow(
    exercise: Exercise,
    done: Boolean,
    hapticsEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .toggleable(
                    value = done,
                    role = Role.Checkbox,
                    onValueChange = { checked ->
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onToggle(checked)
                    }
                )
                // The name is a separate tap target, so the checkbox needs its own label or
                // TalkBack reads an anonymous "checkbox".
                .semantics { contentDescription = "Mark ${exercise.name} done" },
            contentAlignment = Alignment.Center
        ) {
            CheckMark(done = done, enabled = true)
        }
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = MinTouchTarget)
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (done) TextTertiary else TextPrimary,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TargetBadge(exercise.targetLabel)
        }
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button) {
                    if (!openVideo(context, exercise.formVideoUrl)) {
                        Toast.makeText(context, "No app can open videos", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(AppIcons.play),
                contentDescription = "Watch ${exercise.name} video",
                tint = Accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
