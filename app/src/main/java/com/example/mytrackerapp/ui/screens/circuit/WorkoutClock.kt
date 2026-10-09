package com.example.mytrackerapp.ui.screens.circuit

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.mytrackerapp.domain.CircuitFormat
import com.example.mytrackerapp.domain.CircuitPlan
import com.example.mytrackerapp.ui.components.GhostButton
import com.example.mytrackerapp.ui.components.PrimaryButton
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.Surface as SurfaceColor
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/** What the clock shows [elapsed] seconds into a timed circuit. */
internal data class ClockState(
    /** "MINUTE 3 / 12", "ROUND 2 / 8 · WORK", "AMRAP", "FOR TIME". */
    val label: String,
    /** The big number: seconds left in the phase, or elapsed for FOR_TIME. */
    val seconds: Int,
    val finished: Boolean
)

/** Pure so the phase arithmetic is unit-testable. Null for a STANDARD circuit (no clock). */
internal fun clockState(plan: CircuitPlan, elapsed: Int): ClockState? = when (plan.format) {
    CircuitFormat.STANDARD -> null

    CircuitFormat.EMOM -> {
        val total = plan.rounds * 60
        if (elapsed >= total) ClockState("EMOM DONE", 0, true)
        else ClockState("MINUTE ${elapsed / 60 + 1} / ${plan.rounds}", 60 - elapsed % 60, false)
    }

    CircuitFormat.INTERVAL -> {
        val period = plan.workSeconds + plan.restSeconds
        val total = period * plan.rounds
        if (period <= 0 || elapsed >= total) ClockState("INTERVALS DONE", 0, true)
        else {
            val round = elapsed / period + 1
            val into = elapsed % period
            if (into < plan.workSeconds) ClockState("ROUND $round / ${plan.rounds} · WORK", plan.workSeconds - into, false)
            else ClockState("ROUND $round / ${plan.rounds} · REST", period - into, false)
        }
    }

    CircuitFormat.AMRAP -> {
        val total = plan.minutes * 60
        if (elapsed >= total) ClockState("TIME", 0, true)
        else ClockState("AMRAP ${plan.minutes} MIN", total - elapsed, false)
    }

    CircuitFormat.FOR_TIME -> {
        val cap = plan.minutes * 60
        if (cap in 1..elapsed) ClockState("TIME CAP", cap, true)
        else ClockState("FOR TIME", elapsed, false)
    }
}

internal fun formatClock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * A running clock outlives its screen: leave the circuit mid-AMRAP (to check a video, say) and
 * the clock is still counting when you come back. Held per process, keyed by program + slot —
 * a workout clock has no business surviving a force-stop. Monotonic times don't survive a
 * reboot either, so an implausible start is treated as "not started".
 */
internal object ClockMemory {
    private const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
    private val startedAt = mutableMapOf<String, Long>()
    private val rounds = mutableMapOf<String, Int>()

    @Synchronized
    fun start(key: String, nowMs: Long = SystemClock.elapsedRealtime()): Long =
        startedAt[key]?.takeIf { it in 1..nowMs && nowMs - it < MAX_AGE_MS } ?: 0L

    @Synchronized
    fun setStart(key: String, value: Long) {
        if (value == 0L) startedAt.remove(key) else startedAt[key] = value
    }

    @Synchronized
    fun rounds(key: String): Int = rounds[key] ?: 0

    @Synchronized
    fun setRounds(key: String, value: Int) {
        rounds[key] = value
    }
}

/**
 * The running clock above a timed circuit. Wall-clock based (monotonic), so it survives the
 * screen sleeping. AMRAP counts rounds and FOR_TIME records the finish time; either result
 * is saved per circuit slot.
 */
@Composable
fun WorkoutClock(
    plan: CircuitPlan,
    /** Program + week + day + circuit slot, so every slot has its own clock. */
    slotKey: String,
    savedResult: Int?,
    onSaveResult: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var startedAt by remember(slotKey) { mutableLongStateOf(ClockMemory.start(slotKey)) }
    var now by remember(slotKey) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var rounds by remember(slotKey) { mutableIntStateOf(ClockMemory.rounds(slotKey)) }
    fun setStart(value: Long) {
        startedAt = value
        ClockMemory.setStart(slotKey, value)
    }
    fun setRounds(value: Int) {
        rounds = value
        ClockMemory.setRounds(slotKey, value)
    }
    val running = startedAt > 0L
    val elapsed = if (running) ((now - startedAt) / 1000).toInt() else 0
    val state = clockState(plan, elapsed) ?: return

    LaunchedEffect(startedAt) {
        while (startedAt > 0L) {
            now = SystemClock.elapsedRealtime()
            if (clockState(plan, ((now - startedAt) / 1000).toInt())?.finished == true) break
            delay(250)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceColor)
            .border(1.dp, Outline, RoundedCornerShape(Radius.md))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Text(
                    formatClock(if (running) state.seconds else clockState(plan, 0)!!.seconds),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (state.finished) Accent else TextPrimary
                )
            }
            if (plan.format == CircuitFormat.AMRAP) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("ROUNDS", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text("$rounds", style = MaterialTheme.typography.displayMedium, color = Accent)
                }
            }
        }
        savedResult?.let {
            Text(
                "Saved: " + if (plan.format == CircuitFormat.AMRAP) "$it rounds" else formatClock(it),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            when {
                !running -> PrimaryButton(
                    "Start clock",
                    { setStart(SystemClock.elapsedRealtime()); now = startedAt; setRounds(0) },
                    Modifier.weight(1f)
                )
                plan.format == CircuitFormat.AMRAP -> {
                    GhostButton("+1 round", { setRounds(rounds + 1) }, Modifier.weight(1f))
                    PrimaryButton("Save $rounds rounds", { onSaveResult(rounds) }, Modifier.weight(1f))
                }
                plan.format == CircuitFormat.FOR_TIME -> {
                    GhostButton("Reset", { setStart(0L) }, Modifier.weight(1f))
                    PrimaryButton(
                        "Finish · ${formatClock(state.seconds)}",
                        { onSaveResult(state.seconds); setStart(0L) },
                        Modifier.weight(1f)
                    )
                }
                else -> GhostButton("Reset clock", { setStart(0L) }, Modifier.weight(1f))
            }
        }
    }
}
