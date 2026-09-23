package com.example.mytrackerapp.domain.model

enum class Category { BODYWEIGHT, BAND, WARMUP, STRETCH }

enum class TargetType { REPS, SECONDS }

/** Decides circuit membership. Distinct from [Category], which is only the display badge. */
enum class ExerciseSlot { PROGRAM, WARMUP, STRETCH }

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
    val progressionStep: Int = 0
) {
    val isArchived: Boolean get() = archivedAt != null
}

/** Week w targets targetValue + progressionStep * (w - 1). progressionStep = 0 is the
 *  original fixed-target behaviour. */
fun Exercise.targetForWeek(week: Int): Int = targetValue + progressionStep * (week - 1)

/** How far through one circuit the user is. [total] is the circuit's own exercise count. */
data class CircuitProgress(val index: Int, val done: Int, val total: Int) {
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
    val exercisesPerCircuit: Int
) {
    val circuitsTotal: Int get() = circuits.size
    val circuitsDone: Int get() = circuits.count { it.isComplete }
    val exercisesDone: Int get() = circuits.sumOf { it.done }
    val exercisesTotal: Int get() = circuits.sumOf { it.total }
    val exercisesLeft: Int get() = (exercisesTotal - exercisesDone).coerceAtLeast(0)

    /** First incomplete circuit, or null when every circuit of the day is done. */
    val nextCircuit: Int? get() = circuits.firstOrNull { !it.isComplete }?.index
    val allCircuitsComplete: Boolean get() = nextCircuit == null
}

/** What the Today screen is showing: an ordinary training day, or the end of the cycle. */
sealed interface TodayView {
    data class Active(val day: DayState) : TodayView
    data object CycleComplete : TodayView
}

/** One circuit (or one routine) opened for work. */
data class CircuitView(
    val week: Int,
    val day: Int,
    /** >= 1 program circuit, or CIRCUIT_WARMUP / CIRCUIT_STRETCH. */
    val circuit: Int,
    val exercises: List<Exercise>,
    val doneIds: Set<String>,
    /** False for a future day opened as a read-only preview (INVARIANT 4). */
    val editable: Boolean = true
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
    val circuitsTotal: Int get() = circuitsPerDay * days.size

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
    val daysClosedEarly: Int
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
