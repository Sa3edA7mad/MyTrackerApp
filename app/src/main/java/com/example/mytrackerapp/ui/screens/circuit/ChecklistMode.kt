package com.example.mytrackerapp.ui.screens.circuit

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.CircuitView
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.ExerciseRow
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.accent
import com.example.mytrackerapp.ui.components.label
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.SurfaceHigh
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextTertiary

/**
 * Flat list of the whole circuit.
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
    onSwitchToGuided: (() -> Unit)?
) {
    val grouped = view.exercises.groupBy { it.category }

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
                        ExerciseRow(
                            name = exercise.name,
                            targetLabel = exercise.targetLabel,
                            done = exercise.id in view.doneIds,
                            enabled = view.editable,
                            hapticsEnabled = hapticsEnabled,
                            onToggle = { checked -> onToggle(exercise.id, checked) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }
}
