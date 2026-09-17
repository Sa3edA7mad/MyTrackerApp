package com.example.mytrackerapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.AccentMuted
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.CatBand
import com.example.mytrackerapp.ui.theme.CatBodyweight
import com.example.mytrackerapp.ui.theme.CatStretch
import com.example.mytrackerapp.ui.theme.CatWarmUp
import com.example.mytrackerapp.ui.theme.HeatPartial
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.OnAccent
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.OutlineStrong
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.SurfaceHigh
import com.example.mytrackerapp.ui.theme.TextDisabled
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary

/* ------------------------------------------------------------------ category */

val Category.accent: Color
    get() = when (this) {
        Category.BODYWEIGHT -> CatBodyweight
        Category.BAND -> CatBand
        Category.WARMUP -> CatWarmUp
        Category.STRETCH -> CatStretch
    }

val Category.label: String
    get() = when (this) {
        Category.BODYWEIGHT -> "BODYWEIGHT"
        Category.BAND -> "RESISTANCE BAND"
        Category.WARMUP -> "WARM-UP"
        Category.STRETCH -> "STRETCH"
    }

/* ------------------------------------------------------------------- buttons */

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (enabled) Accent else SurfaceHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.base),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) OnAccent else TextTertiary
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = TextSecondary
) {
    Box(
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(Radius.pill))
            .border(1.dp, if (enabled) OutlineStrong else Outline, RoundedCornerShape(Radius.pill))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) tint else TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* --------------------------------------------------------------- chips/badges */

@Composable
fun CategoryChip(category: Category, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(category.accent.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.labelSmall,
            color = category.accent
        )
    }
}

@Composable
fun TargetBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceHigh)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            maxLines = 1
        )
    }
}

/* ------------------------------------------------------------------ checkbox */

@Composable
private fun CheckMark(done: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val borderColor = when {
        done -> Accent
        enabled -> OutlineStrong
        else -> TextDisabled // border only — TextDisabled must never render text
    }
    Box(
        modifier = modifier
            .size(21.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (done) Accent else Color.Transparent)
            .border(1.5.dp, borderColor, RoundedCornerShape(Radius.sm)),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Canvas(Modifier.size(13.dp)) {
                val w = size.width
                val h = size.height
                val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                drawLine(
                    color = OnAccent,
                    start = Offset(w * 0.12f, h * 0.55f),
                    end = Offset(w * 0.40f, h * 0.82f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = OnAccent,
                    start = Offset(w * 0.40f, h * 0.82f),
                    end = Offset(w * 0.88f, h * 0.20f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * The whole row is the toggle target, not the 21dp box — that is what gets this to a
 * 48dp tap target and gives TalkBack a single checkable node.
 */
@Composable
fun ExerciseRow(
    name: String,
    targetLabel: String,
    done: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .toggleable(
                value = done,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { checked ->
                    if (hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onToggle(checked)
                }
            )
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        CheckMark(done = done, enabled = enabled)
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                !enabled -> TextTertiary
                done -> TextTertiary
                else -> TextPrimary
            },
            textDecoration = if (done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TargetBadge(targetLabel)
    }
}

/* -------------------------------------------------------------------- rings */

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 104.dp,
    strokeWidth: Dp = 9.dp,
    color: Color = Accent,
    trackColor: Color = SurfaceHigh,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .size(diameter)
            .progressSemantics(p, 0f..1f)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = strokeWidth.toPx()
            val inset = s / 2f
            val arcSize = Size(size.width - s, size.height - s)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = s)
            )
            if (p > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = s, cap = StrokeCap.Round)
                )
            }
        }
        // The ring already carries progress semantics; don't let the label double-read.
        Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/* ------------------------------------------------------------------ circuits */

enum class CircuitCardState { DONE, ACTIVE, UPCOMING, LOCKED }

@Composable
fun CircuitCard(
    index: Int,
    done: Int,
    total: Int,
    state: CircuitCardState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clickable = state != CircuitCardState.LOCKED
    val alpha = if (state == CircuitCardState.LOCKED) 0.4f else 1f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(Radius.md))
            .background(
                if (state == CircuitCardState.ACTIVE) Accent.copy(alpha = 0.06f) else SurfaceColor
            )
            .border(
                width = if (state == CircuitCardState.ACTIVE) 1.5.dp else 1.dp,
                color = if (state == CircuitCardState.ACTIVE) Accent else Outline,
                shape = RoundedCornerShape(Radius.md)
            )
            .clickable(enabled = clickable, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(if (state == CircuitCardState.DONE) Accent else Color.Transparent)
                .border(
                    1.5.dp,
                    when (state) {
                        CircuitCardState.DONE -> Accent
                        CircuitCardState.ACTIVE -> Accent
                        else -> OutlineStrong
                    },
                    RoundedCornerShape(Radius.sm)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state == CircuitCardState.DONE) "" else "$index",
                style = MaterialTheme.typography.labelSmall,
                color = if (state == CircuitCardState.ACTIVE) Accent else TextTertiary
            )
        }
        Text(
            text = "Circuit $index",
            style = MaterialTheme.typography.titleMedium,
            color = if (state == CircuitCardState.DONE) TextTertiary else TextPrimary.copy(alpha = alpha),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$done/$total",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary
        )
    }
}

/* --------------------------------------------------------------------- stats */

@Composable
fun StatTile(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceColor)
            .border(1.dp, Outline, RoundedCornerShape(Radius.md))
            .padding(Spacing.md)
    ) {
        Text(value, style = MaterialTheme.typography.displayMedium, color = Accent, maxLines = 1)
        Text(
            caption.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ------------------------------------------------------------- day cells/strip */

enum class DayCell { COMPLETE, PARTIAL, CURRENT, EMPTY }

private val DayCell.fill: Color
    get() = when (this) {
        DayCell.COMPLETE -> Accent
        DayCell.PARTIAL -> HeatPartial
        DayCell.CURRENT -> Color.Transparent
        DayCell.EMPTY -> SurfaceHigh
    }

@Composable
fun HeatCell(state: DayCell, modifier: Modifier = Modifier, label: String? = null) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(state.fill)
            .then(
                if (state == DayCell.CURRENT) {
                    Modifier.border(1.5.dp, Accent, RoundedCornerShape(Radius.sm))
                } else Modifier
            )
            .then(
                if (label != null) Modifier.semantics { contentDescription = label } else Modifier
            )
    )
}

@Composable
fun WeekStrip(days: List<DayCell>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        days.forEachIndexed { i, state ->
            Box(
                modifier = Modifier
                    .size(31.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(state.fill)
                    .then(
                        if (state == DayCell.CURRENT) {
                            Modifier.border(1.5.dp, Accent, RoundedCornerShape(Radius.md))
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${i + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (state) {
                        DayCell.COMPLETE -> OnAccent
                        DayCell.PARTIAL -> TextPrimary
                        DayCell.CURRENT -> Accent
                        DayCell.EMPTY -> TextTertiary
                    }
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ structure */

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, color: Color = TextTertiary) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier.padding(top = 15.dp, bottom = 3.dp)
    )
}

/** Two rounded bars. Icons.Default.Pause does not exist in the bundled core icon set. */
@Composable
fun PauseIcon(modifier: Modifier = Modifier, tint: Color = TextSecondary) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) {
            Box(
                Modifier
                    .width(4.dp)
                    .heightIn(min = 14.dp)
                    .size(width = 4.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint)
            )
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent)
    }
}

/**
 * Bottom-pinned action area. The Today screen's primary CTA lives here so it stays on
 * screen in weeks 3 and 4, where 6-7 circuit cards push the content past the fold.
 */
@Composable
fun StickyCtaBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CanvasColor)
            .drawBehind {
                drawLine(
                    color = Outline,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content
    )
}

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 360)
@Composable
private fun ComponentGalleryPreview() {
    MyTrackerAppTheme {
        Column(
            Modifier
                .background(CanvasColor)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            PrimaryButton("Start circuit 4", {})
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GhostButton("Skip", {}, Modifier.weight(1f))
                GhostButton("Checklist", {}, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CategoryChip(Category.BODYWEIGHT)
                CategoryChip(Category.BAND)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CategoryChip(Category.WARMUP)
                CategoryChip(Category.STRETCH)
            }
            SectionHeader("Exercise rows")
            ExerciseRow("Squat", "5 reps", done = true, onToggle = {})
            ExerciseRow("Dead Hang", "15 sec", done = false, onToggle = {})
            ExerciseRow("External Rotation", "10 ea.", done = false, onToggle = {}, enabled = false)
            SectionHeader("Circuits")
            CircuitCard(1, 13, 13, CircuitCardState.DONE, {})
            CircuitCard(4, 0, 13, CircuitCardState.ACTIVE, {})
            CircuitCard(5, 0, 13, CircuitCardState.UPCOMING, {})
            CircuitCard(6, 0, 13, CircuitCardState.LOCKED, {})
            SectionHeader("Ring, stats, week strip")
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressRing(progress = 0.6f, contentDescription = "3 of 5 circuits done") {
                    Text("3", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
                }
                StatTile("11", "Day streak", Modifier.weight(1f))
                StatTile("68%", "Cycle", Modifier.weight(1f))
            }
            WeekStrip(
                listOf(
                    DayCell.COMPLETE, DayCell.COMPLETE, DayCell.CURRENT,
                    DayCell.EMPTY, DayCell.EMPTY, DayCell.PARTIAL
                )
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PauseIcon()
                Text("Pause icon", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            StickyCtaBar { PrimaryButton("Sticky CTA", {}) }
        }
    }
}
