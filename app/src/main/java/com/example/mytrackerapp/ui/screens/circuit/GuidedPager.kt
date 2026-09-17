package com.example.mytrackerapp.ui.screens.circuit

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mytrackerapp.data.prefs.Settings
import com.example.mytrackerapp.domain.model.CircuitView
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.TargetType
import com.example.mytrackerapp.domain.model.targetForWeek
import com.example.mytrackerapp.ui.components.AppIcons
import com.example.mytrackerapp.ui.components.CategoryChip
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.components.PrimaryButton
import com.example.mytrackerapp.ui.components.StickyCtaBar
import com.example.mytrackerapp.ui.openVideo
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.SurfaceHigh
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * Stage within one exercise.
 *
 * Every `perSide` exercise runs SIDE_1 -> SWITCH -> SIDE_2 regardless of whether its
 * target is reps or seconds, and writes a single completion row at the end. In v1 of the
 * plan only the timed ones got a switch step, which was an inconsistency.
 */
private const val STAGE_FIRST = 0
private const val STAGE_SWITCH = 1
private const val STAGE_SECOND = 2

@Composable
fun GuidedPager(
    view: CircuitView,
    settings: Settings,
    overline: String,
    /** Marks the exercise done, then invokes the second parameter to advance the pager —
     *  giving the caller a chance to collect reps/load first (T21) before moving on. */
    onDone: (String, () -> Unit) -> Unit,
    onExit: () -> Unit,
    onFinished: () -> Unit,
    onSwitchToChecklist: (() -> Unit)?,
    /** Bump to send the pager back to the first unticked exercise. */
    restartKey: Int = 0,
    /** Extra full-width action, e.g. "Skip warm-up" on the routine screens. */
    secondaryAction: Pair<String, () -> Unit>? = null
) {
    val identity = "${view.week}/${view.day}/${view.circuit}"
    var index by rememberSaveable(identity, restartKey) { mutableIntStateOf(view.firstUndoneIndex) }
    var stage by rememberSaveable(index, identity) { mutableIntStateOf(STAGE_FIRST) }

    val exercise = view.exercises.getOrNull(index)
    if (exercise == null) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }

    KeepScreenOn(settings.keepScreenOn)

    val target = exercise.targetForWeek(view.week)
    val timer = rememberHoldTimer(
        totalSeconds = target,
        resetKey = "$identity-$index-$stage"
    )
    val timed = exercise.targetType == TargetType.SECONDS
    if (timed) TimerCues(timer, settings.soundCues, settings.haptics)

    fun goNext() {
        if (index + 1 >= view.exercises.size) onFinished() else {
            index += 1
            stage = STAGE_FIRST
        }
    }

    fun advance() {
        if (!view.editable) return
        if (exercise.perSide) {
            when (stage) {
                STAGE_FIRST -> stage = STAGE_SWITCH
                STAGE_SWITCH -> stage = STAGE_SECOND
                else -> onDone(exercise.id) { goNext() }
            }
        } else {
            onDone(exercise.id) { goNext() }
        }
    }

    // Auto-advance once the hold completes, after letting the completion tone land.
    //
    // `advance()` is called DIRECTLY here, and `index`/`stage` are effect keys so this
    // relaunches on every step, capturing that composition's locals.
    //
    // Do NOT reintroduce `rememberUpdatedState(::advance)`. A Kotlin callable reference
    // to a local function implements equals() by owner/name/signature, so successive
    // recompositions produce references that compare EQUAL. mutableStateOf uses
    // structural equality, skips the write, and the State keeps the closure from the
    // first composition forever — which made every hold re-complete exercise #1 instead
    // of the one that just finished.
    LaunchedEffect(timer.finished, timed, settings.autoAdvanceTimer, index, stage) {
        if (timed && timer.finished && timer.started && settings.autoAdvanceTimer) {
            delay(700)
            advance()
        }
    }

    Column(Modifier.fillMaxSize()) {

        Header(
            overline = overline,
            position = "${index + 1}/${view.total}",
            onExit = onExit
        )
        SegmentBar(
            total = view.exercises.size,
            doneFlags = view.exercises.map { it.id in view.doneIds },
            current = index,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!view.editable) {
                PreviewBanner()
            }

            Spacer(Modifier.height(Spacing.xl))
            CategoryChip(exercise.category)
            Spacer(Modifier.height(Spacing.md))
            Text(
                exercise.name,
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            if (exercise.muscles.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    exercise.muscles,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
            if (exercise.perSide) {
                Spacer(Modifier.height(Spacing.sm))
                SideBadge(stage)
            }

            Spacer(Modifier.height(Spacing.xl))
            when {
                stage == STAGE_SWITCH -> SwitchSidesPanel()
                timed -> HoldTimerDial(timer)
                else -> RepTarget(exercise, target)
            }

            Spacer(Modifier.height(Spacing.xl))
            InstructionCard(exercise)
            Spacer(Modifier.height(Spacing.lg))
        }

        StickyCtaBar {
            PrimaryActions(
                target = target,
                stage = stage,
                timed = timed,
                timer = timer,
                enabled = view.editable,
                onAdvance = { advance() }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GhostButton("Skip", onClick = { goNext() }, modifier = Modifier.weight(1f))
                if (onSwitchToChecklist != null) {
                    GhostButton(
                        "⇄ Checklist",
                        onClick = onSwitchToChecklist,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (secondaryAction != null) {
                GhostButton(
                    text = secondaryAction.first,
                    onClick = secondaryAction.second,
                    modifier = Modifier.fillMaxWidth(),
                    tint = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun PrimaryActions(
    target: Int,
    stage: Int,
    timed: Boolean,
    timer: HoldTimerState,
    enabled: Boolean,
    onAdvance: () -> Unit
) {
    when {
        stage == STAGE_SWITCH ->
            PrimaryButton("Continue · side 2", onAdvance, enabled = enabled)

        !timed ->
            PrimaryButton("✓  Done", onAdvance, enabled = enabled)

        timer.finished && timer.started ->
            PrimaryButton("✓  Done", onAdvance, enabled = enabled)

        timer.running -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GhostButton("❚❚ Pause", { timer.pause() }, Modifier.weight(1f))
            GhostButton("Reset", { timer.reset() }, Modifier.weight(1f))
        }

        timer.started -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GhostButton("Reset", { timer.reset() }, Modifier.weight(1f))
            PrimaryButton("Resume", { timer.start() }, Modifier.weight(1f), enabled = enabled)
        }

        else ->
            PrimaryButton("Start · $target sec", { timer.start() }, enabled = enabled)
    }
}

@Composable
private fun Header(overline: String, position: String, onExit: () -> Unit) {
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
            overline,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Text(position, style = MaterialTheme.typography.labelMedium, color = TextTertiary)
    }
}

@Composable
private fun SegmentBar(
    total: Int,
    doneFlags: List<Boolean>,
    current: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.padding(top = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            doneFlags.getOrElse(i) { false } -> Accent
                            i == current -> TextTertiary
                            else -> SurfaceHigh
                        }
                    )
            )
        }
    }
}

@Composable
private fun SideBadge(stage: Int) {
    val label = when (stage) {
        STAGE_SECOND -> "SIDE 2"
        STAGE_SWITCH -> "SIDE 1 DONE"
        else -> "SIDE 1"
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(SurfaceHigh)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun SwitchSidesPanel() {
    Box(
        Modifier
            .size(180.dp)
            .clip(CircleShape)
            .border(2.dp, Accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⇄", style = MaterialTheme.typography.displayLarge, color = Accent)
            Text(
                "SWITCH SIDES",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun RepTarget(exercise: Exercise, target: Int) {
    Box(
        Modifier
            .size(180.dp)
            .clip(CircleShape)
            .border(2.dp, Outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$target",
                style = MaterialTheme.typography.displayLarge,
                color = Accent
            )
            Text(
                if (exercise.targetType == TargetType.REPS) "REPS" else "SEC",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun InstructionCard(exercise: Exercise) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceColor)
            .border(1.dp, Outline, RoundedCornerShape(Radius.md))
            .padding(Spacing.md)
    ) {
        Text("HOW TO", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            exercise.instructions,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(Spacing.md))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(role = Role.Button) { openVideo(context, exercise.videoUrl) }
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(
                painterResource(AppIcons.play),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                "WATCH FORM VIDEO",
                style = MaterialTheme.typography.labelSmall,
                color = Accent
            )
        }
    }
}

@Composable
private fun PreviewBanner() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md)
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceHigh)
            .padding(Spacing.md)
    ) {
        Text(
            "Preview — finish the current day before training this one.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
