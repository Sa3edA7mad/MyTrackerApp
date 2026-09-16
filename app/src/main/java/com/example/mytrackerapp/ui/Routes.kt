package com.example.mytrackerapp.ui

enum class RoutineType(val slug: String) {
    WARMUP("warmup"),
    STRETCH("stretch");

    companion object {
        fun fromSlug(slug: String?): RoutineType =
            entries.firstOrNull { it.slug == slug } ?: WARMUP
    }
}

/**
 * Route builders live here so no call site ever hand-concatenates a path.
 */
object Routes {
    const val TODAY = "today"
    const val PROGRAM = "program"
    const val PROGRESS = "progress"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val CYCLE_COMPLETE = "cycleComplete"

    const val ARG_WEEK = "week"
    const val ARG_DAY = "day"
    const val ARG_CIRCUIT = "circuit"
    const val ARG_TYPE = "type"
    const val ARG_ID = "id"

    const val CIRCUIT_PATTERN = "circuit/{$ARG_WEEK}/{$ARG_DAY}/{$ARG_CIRCUIT}"
    const val ROUTINE_PATTERN = "routine/{$ARG_TYPE}"
    const val EXERCISE_PATTERN = "exercise/{$ARG_ID}"

    fun circuit(week: Int, day: Int, circuit: Int) = "circuit/$week/$day/$circuit"
    fun routine(type: RoutineType) = "routine/${type.slug}"
    fun exercise(id: String) = "exercise/$id"

    /** The four tabbed destinations. Everything else is immersive (no bottom bar). */
    val TABBED = setOf(TODAY, PROGRAM, PROGRESS, LIBRARY)
}
