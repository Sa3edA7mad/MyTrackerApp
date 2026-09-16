package com.example.mytrackerapp.ui.screens.circuit

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.mytrackerapp.ui.components.ProgressRing
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlin.math.ceil

/**
 * Countdown state driven by the wall clock, not by decrementing a counter.
 *
 * [SystemClock.elapsedRealtime] is monotonic and keeps counting through deep sleep, so
 * the remaining time stays correct across a phone call, a doze, or the user backgrounding
 * the app mid-hold. It is also immune to the wall-clock time being changed underneath us,
 * which `System.currentTimeMillis()` is not.
 */
@Stable
class HoldTimerState(
    val totalSeconds: Int,
    /**
     * Injected so the countdown is unit-testable on the JVM without SystemClock.
     * Production always uses the monotonic clock.
     */
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private var endAtMs by mutableLongStateOf(0L)

    var remainingMs by mutableLongStateOf(totalSeconds * 1000L)
        private set

    var running by mutableStateOf(false)
        private set

    val totalMs: Long get() = totalSeconds * 1000L
    val secondsLeft: Int get() = ceil(remainingMs / 1000.0).toInt()
    val finished: Boolean get() = remainingMs <= 0L
    val started: Boolean get() = running || remainingMs < totalMs

    /** Fraction still to go, so the ring drains as the hold progresses. */
    val progress: Float
        get() = if (totalMs == 0L) 0f else (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)

    fun start() {
        if (finished) reset()
        endAtMs = nowMs() + remainingMs
        running = true
    }

    fun pause() {
        if (!running) return
        remainingMs = (endAtMs - nowMs()).coerceAtLeast(0L)
        running = false
    }

    fun reset() {
        remainingMs = totalMs
        running = false
    }

    fun tick() {
        if (!running) return
        remainingMs = (endAtMs - nowMs()).coerceAtLeast(0L)
        if (remainingMs == 0L) running = false
    }
}

@Composable
fun rememberHoldTimer(totalSeconds: Int, resetKey: Any): HoldTimerState {
    val state = remember(resetKey) { HoldTimerState(totalSeconds) }
    LaunchedEffect(state, state.running) {
        while (state.running) {
            state.tick()
            delay(100)
        }
    }
    return state
}

/**
 * Keeps the screen awake while a hold is running and restores whatever the value was
 * before, rather than blindly switching it off on dispose.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = previous }
    }
}

/**
 * Audible + haptic countdown cues.
 *
 * Audio is the primary channel here and defaults to on: during a Dead Hang the phone is
 * on the floor and the user is hanging from a bar, so a vibration is unreachable.
 */
@Composable
fun TimerCues(timer: HoldTimerState, soundEnabled: Boolean, hapticsEnabled: Boolean) {
    val haptics = LocalHapticFeedback.current

    val tone = remember(soundEnabled) {
        if (soundEnabled) {
            runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 80) }.getOrNull()
        } else null
    }
    DisposableEffect(tone) {
        onDispose { runCatching { tone?.release() } }
    }

    // Final three seconds: short beep on each.
    LaunchedEffect(timer.secondsLeft, timer.running) {
        if (timer.running && timer.secondsLeft in 1..3) {
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Zero: a longer, distinct tone.
    LaunchedEffect(timer.finished) {
        if (timer.finished && timer.started) {
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400) }
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
fun HoldTimerDial(timer: HoldTimerState, modifier: Modifier = Modifier) {
    // Announce only at the thresholds, not once a second — otherwise TalkBack talks over
    // the whole hold.
    val announcement = when {
        timer.finished && timer.started -> "Hold complete"
        !timer.started -> "${timer.totalSeconds} second hold, not started"
        timer.secondsLeft <= 5 -> "5 seconds left"
        timer.secondsLeft <= 10 -> "10 seconds left"
        else -> "${timer.totalSeconds} second hold"
    }

    ProgressRing(
        progress = timer.progress,
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = announcement
        },
        diameter = 180.dp,
        strokeWidth = 11.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = timer.secondsLeft.toString().padStart(2, '0'),
                style = MaterialTheme.typography.displayLarge,
                color = if (timer.finished && timer.started) Accent else TextPrimary
            )
            Row {
                Text(
                    text = "OF ${timer.totalSeconds} SEC",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}
