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
    const val PROGRAM_NEW = "programNew"
    const val MEASURE = "measure"
    const val MEASURE_CATALOG = "measureCatalog"

    const val ARG_WEEK = "week"
    const val ARG_DAY = "day"
    const val ARG_CIRCUIT = "circuit"
    const val ARG_TYPE = "type"
    const val ARG_ID = "id"
    const val ARG_METRIC_ID = "metricId"
    const val ARG_PROGRAM = "program"
    const val ARG_KEY = "key"

    const val CIRCUIT_PATTERN = "circuit/{$ARG_PROGRAM}/{$ARG_WEEK}/{$ARG_DAY}/{$ARG_CIRCUIT}"
    const val ROUTINE_PATTERN = "routine/{$ARG_PROGRAM}/{$ARG_TYPE}"
    const val RULES_PATTERN = "rules/{$ARG_PROGRAM}"
    const val CYCLE_COMPLETE_PATTERN = "cycleComplete/{$ARG_PROGRAM}"
    const val PROGRAM_EDIT_PATTERN = "programEdit/{$ARG_PROGRAM}"
    const val CIRCUIT_EDIT_PATTERN = "circuitEdit/{$ARG_PROGRAM}/{$ARG_KEY}"
    const val EXERCISE_PATTERN = "exercise/{$ARG_ID}"
    const val EXERCISE_EDIT_PATTERN = "exerciseEdit/{$ARG_ID}"
    const val METRIC_HISTORY_PATTERN = "measure/{$ARG_METRIC_ID}"

    /** id = "new" means create rather than edit. */
    const val EXERCISE_EDIT_NEW_ID = "new"

    fun circuit(programId: Long, week: Int, day: Int, circuit: Int) = "circuit/$programId/$week/$day/$circuit"
    fun routine(programId: Long, type: RoutineType) = "routine/$programId/${type.slug}"
    fun rules(programId: Long) = "rules/$programId"
    fun cycleComplete(programId: Long) = "cycleComplete/$programId"
    fun programEdit(programId: Long) = "programEdit/$programId"
    /** [key] is a plan circuit key, or `warmup` / `stretch`. */
    fun circuitEdit(programId: Long, key: String) = "circuitEdit/$programId/$key"
    fun exercise(id: String) = "exercise/$id"
    fun exerciseEdit(id: String = EXERCISE_EDIT_NEW_ID) = "exerciseEdit/$id"
    fun metricHistory(metricId: String) = "measure/$metricId"

    /** The four tabbed destinations. Everything else is immersive (no bottom bar). */
    val TABBED = setOf(TODAY, PROGRAM, PROGRESS, LIBRARY)
}
