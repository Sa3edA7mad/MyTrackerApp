package com.example.mytrackerapp.domain.model

import com.example.mytrackerapp.domain.CircuitPlan
import com.example.mytrackerapp.domain.youtubeSearchUrl

/** The first four are the Home program's; the rest are the library workbook's sheets. */
enum class Category { BODYWEIGHT, BAND, WARMUP, STRETCH, GYM, CROSSFIT, MOBILITY, CORE }

enum class TargetType { REPS, SECONDS }

/**
 * Decides membership of slot-backed circuits (the Home program's). Distinct from [Category],
 * which is only the display badge. LIBRARY is in no slot list — programs pick it explicitly.
 */
enum class ExerciseSlot { PROGRAM, WARMUP, STRETCH, LIBRARY }

data class Exercise(
    val id: String,
    val name: String,
    val category: Category,
    val muscles: String,
    val instructions: String,
    val targetType: TargetType,
    val targetValue: Int,
    val perSide: Boolean,
    val targetLabel: String,
    val videoUrl: String,
    val sortOrder: Int,
    val slot: ExerciseSlot = ExerciseSlot.PROGRAM,
    val enabled: Boolean = true,
    val archivedAt: Long? = null,
    val isCustom: Boolean = false,
    val tracksReps: Boolean = false,
    val tracksLoad: Boolean = false,
    val defaultLoadKg: Double? = null,
    val defaultBandLevel: String? = null,
    val progressionStep: Int = 0,
    val equipment: String = "",
    /** BEGINNER | INTERMEDIATE | ADVANCED, or empty. */
    val level: String = "",
    val cue: String = "",
    val videoTitle: String = "",
    val videoChannel: String = ""
) {
    val isArchived: Boolean get() = archivedAt != null
}

/**
 * The stored link, or a YouTube search for the name when none was entered — so every
 * exercise, including ones added in the editor, has a form video.
 *
 * Resolved here rather than in `toDomain()` on purpose: the editor loads the raw
 * [Exercise.videoUrl], so a blank field stays blank and the search follows later renames.
 */
val Exercise.formVideoUrl: String get() = videoUrl.ifBlank { youtubeSearchUrl(name) }

/** Week w targets targetValue + progressionStep * (w - 1). progressionStep = 0 is the
 *  original fixed-target behaviour. */
fun Exercise.targetForWeek(week: Int): Int = targetValue + progressionStep * (week - 1)

/** How far through one circuit the user is. [total] is the circuit's own exercise count. */
data class CircuitProgress(
    val index: Int,
    val done: Int,
    val total: Int,
    /** The plan circuit's name. "Circuit" (Home's) means unnamed. */
    val name: String = ""
) {
    /** Extra label for a named circuit; null for Home's plain numbered circuits. */
    val subtitle: String? get() = name.takeIf { it.isNotBlank() && it != "Circuit" }

    val isComplete: Boolean get() = done >= total
    val isStarted: Boolean get() = done > 0
}

/**
 * Everything the Today screen needs. All totals are derived (INVARIANT 5) — nothing
 * here is cached in the database.
 */
data class DayState(
    val week: Int,
    val day: Int,
    val circuits: List<CircuitProgress>,
    val warmUpDone: Boolean,
    val stretchDone: Boolean,
    val closed: Boolean,
    val exercisesPerCircuit: Int,
    val warmUpEnabled: Boolean = true,
    val stretchEnabled: Boolean = true,
    val warmUpCount: Int = 8,
    val stretchCount: Int = 8,
    /** False when the day's circuits differ in size, so "N exercises each" would be wrong. */
    val uniformCircuits: Boolean = true
) {
    val circuitsTotal: Int get() = circuits.size
    val circuitsDone: Int get() = circuits.count { it.isComplete }
    val exercisesDone: Int get() = circuits.sumOf { it.done }
    val exercisesTotal: Int get() = circuits.sumOf { it.total }
    val exercisesLeft: Int get() = (exercisesTotal - exercisesDone).coerceAtLeast(0)

    /** First incomplete circuit, or null when every circuit of the day is done. */
    val nextCircuit: Int? get() = circuits.firstOrNull { !it.isComplete }?.index
    val allCircuitsComplete: Boolean get() = nextCircuit == null

    /** Warm-up still stands between the user and their next circuit. */
    val warmUpPending: Boolean get() = warmUpEnabled && !warmUpDone

    /** Circuits are finished but the stretch that closes the day is not. */
    val stretchPending: Boolean get() = stretchEnabled && !stretchDone
}

/** What the Today screen is showing: an ordinary training day, or the end of the cycle. */
sealed interface TodayView {
    data class Active(val day: DayState) : TodayView
    /** [days] is the number of training days in the finished cycle, for the copy. */
    data class CycleComplete(val days: Int) : TodayView
}

/** One circuit (or one routine) opened for work. */
data class CircuitView(
    val week: Int,
    val day: Int,
    /** >= 1 program circuit, or CIRCUIT_WARMUP / CIRCUIT_STRETCH. */
    val circuit: Int,
    /** One entry per tick. For a multi-set exercise each set is its own entry, whose `id`
     *  is the step key (`id#set`) — see [com.example.mytrackerapp.domain.CircuitStep]. */
    val exercises: List<Exercise>,
    val doneIds: Set<String>,
    /** False for a future day opened as a read-only preview (INVARIANT 4). */
    val editable: Boolean = true,
    /** "Set 2 of 5" by step key, for multi-set steps only. */
    val setLabels: Map<String, String> = emptyMap(),
    /** The circuit's name, format and rest. Null for routines and pre-program cycles. */
    val plan: CircuitPlan? = null
) {
    val done: Int get() = exercises.count { it.id in doneIds }
    val total: Int get() = exercises.size
    val isComplete: Boolean get() = done >= total
    val firstUndoneIndex: Int
        get() = exercises.indexOfFirst { it.id !in doneIds }.let { if (it < 0) 0 else it }
}

/** One cell of the 4x6 progress map, and one day row on the Program screen. */
data class DaySummary(
    val week: Int,
    val day: Int,
    val done: Int,
    val total: Int,
    val closed: Boolean,
    /** Per-circuit breakdown, so a day on the Program screen can expand into its circuits. */
    val circuits: List<CircuitProgress> = emptyList()
) {
    val isComplete: Boolean get() = done >= total
    val isPartial: Boolean get() = done in 1 until total
    val isUntouched: Boolean get() = done == 0 && !closed
}

data class WeekState(
    val week: Int,
    val circuitsPerDay: Int,
    val days: List<DaySummary>,
    val isCurrent: Boolean,
    val exercisesPerCircuit: Int
) {
    /** Counted per day: with a rotation, days can run different numbers of circuits. */
    val circuitsTotal: Int get() = days.sumOf { it.circuits.size }

    /** Counts complete circuits directly rather than dividing, so this stays correct even
     *  when circuits carry different sizes. Requires [DaySummary.circuits] to be populated. */
    val circuitsDone: Int get() = days.sumOf { day -> day.circuits.count { it.isComplete } }
    val exercisesDone: Int get() = days.sumOf { it.done }
    val exercisesTotal: Int get() = days.sumOf { it.total }
}

data class ExerciseTally(val exerciseId: String, val name: String, val count: Int)

data class DayTally(val date: java.time.LocalDate, val count: Int)

data class ExerciseDetail(
    val exercise: Exercise,
    val totalThisCycle: Int,
    /** Ascending by date, at most 7 entries. Empty until the exercise has been done. */
    val recent: List<DayTally>
) {
    val hasHistory: Boolean get() = totalThisCycle > 0
    val maxCount: Int get() = recent.maxOfOrNull { it.count } ?: 0
}

data class CycleStats(
    val streak: Int,
    val exercisesDone: Int,
    val exercisesTotal: Int,
    val circuitsDone: Int,
    val circuitsTotal: Int,
    val daysTrained: Int,
    val weeks: List<WeekState>,
    val heat: List<DaySummary>,
    val mostDone: List<ExerciseTally>
) {
    val percent: Int
        get() = if (exercisesTotal == 0) 0 else (exercisesDone * 100) / exercisesTotal
}

/** Everything the end-of-cycle screen reports. */
data class CycleSummary(
    val exercisesDone: Int,
    val exercisesTotal: Int,
    val circuitsDone: Int,
    val circuitsTotal: Int,
    val daysTrained: Int,
    /** Longest consecutive run in the cycle, not the run still alive today. */
    val bestStreak: Int,
    /** Calendar days from the first session to now. */
    val elapsedDays: Int,
    val daysClosedEarly: Int,
    /** Program length under the cycle's rules, for the headline. */
    val weeks: Int = 4
) {
    val percent: Int
        get() = if (exercisesTotal == 0) 0 else (exercisesDone * 100) / exercisesTotal
    val isPerfect: Boolean get() = exercisesDone >= exercisesTotal
}

/**
 * The catalog seeds asynchronously on first launch, so [Loading] is a real state that
 * every screen must render — never draw a screen against an empty catalog as though
 * the user had simply finished everything.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/** A training program. Several can be [active] at once; archived ones keep their history. */
data class Program(
    val id: Long,
    val name: String,
    val active: Boolean,
    val archived: Boolean
)
