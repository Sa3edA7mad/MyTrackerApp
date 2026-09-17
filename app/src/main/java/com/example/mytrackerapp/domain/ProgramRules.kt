package com.example.mytrackerapp.domain

/**
 * Every rule of the program as one immutable value.
 *
 * Replaces the top-level constants that used to live in Program.kt. A cycle is read against
 * the snapshot stored in its `cycle_rules` row (INVARIANT 7), never against the editable
 * `program_rules` row, so a finished cycle's percentages never change meaning.
 *
 * Pure Kotlin — no Android imports. Unit-tested on the JVM.
 */
data class ProgramRules(
    val weeks: Int = 4,
    val daysPerWeek: Int = 6,
    /** One entry per week. Size must equal [weeks]. */
    val circuitsPerWeek: List<Int> = listOf(4, 5, 6, 7),
    /** Count of enabled PROGRAM-slot exercises. Derived from the catalog from T13 onward. */
    val exercisesPerCircuit: Int = 13,
    val warmUpCount: Int = 8,
    val stretchCount: Int = 8,
    /** A set finished at 01:00 belongs to the day that just ended. 0..23. */
    val dayRolloverHour: Int = 4,
    val warmUpEnabled: Boolean = true,
    val stretchEnabled: Boolean = true,
    /** INVARIANT 2 as a rule. Off = warm-up and stretch never enter a total. */
    val countRoutinesInTotals: Boolean = false,
    /** INVARIANT 4 as a rule. Off = any day can be edited at any time. */
    val lockFutureDays: Boolean = true
) {

    val routineExercisesPerDay: Int =
        (if (warmUpEnabled) warmUpCount else 0) + (if (stretchEnabled) stretchCount else 0)

    /** All training days in program order. Computed once per instance. */
    val allPositions: List<Position> =
        (1..weeks).flatMap { w -> (1..daysPerWeek).map { d -> Position(w, d) } }

    fun circuitsForWeek(week: Int): Int {
        require(week in 1..weeks) { "week must be 1..$weeks, was $week" }
        return circuitsPerWeek[week - 1]
    }

    fun exercisesPerDay(week: Int): Int =
        circuitsForWeek(week) * exercisesPerCircuit +
            (if (countRoutinesInTotals) routineExercisesPerDay else 0)

    fun exercisesForWeek(week: Int): Int = exercisesPerDay(week) * daysPerWeek

    fun totalExercisesInCycle(): Int = (1..weeks).sumOf { exercisesForWeek(it) }

    fun totalCircuitsInCycle(): Int = (1..weeks).sumOf { circuitsForWeek(it) * daysPerWeek }

    /** INVARIANT 3. */
    fun isDaySettled(week: Int, doneCount: Int, closed: Boolean): Boolean =
        closed || doneCount >= exercisesPerDay(week)

    /** First unsettled day in program order, or null when the cycle is finished. */
    fun nextPosition(
        doneByPosition: Map<Position, Int>,
        closedPositions: Set<Position>
    ): Position? = allPositions.firstOrNull { p ->
        !isDaySettled(p.week, doneByPosition[p] ?: 0, p in closedPositions)
    }

    fun isAfter(candidate: Position, current: Position): Boolean =
        allPositions.indexOf(candidate) > allPositions.indexOf(current)

    /**
     * INVARIANT 8: a completion row that no longer fits the rules is *orphaned*, not deleted.
     * Every aggregate filters through this so orphans cannot inflate a total.
     */
    fun isValidSlot(week: Int, day: Int, circuit: Int): Boolean {
        if (week !in 1..weeks || day !in 1..daysPerWeek) return false
        return when (circuit) {
            CIRCUIT_WARMUP -> warmUpEnabled
            CIRCUIT_STRETCH -> stretchEnabled
            else -> circuit in 1..circuitsForWeek(week)
        }
    }

    /** Counts only slots that still exist under these rules. */
    fun countsProgramSlot(circuit: Int): Boolean =
        circuit >= 1 || (countRoutinesInTotals && (circuit == CIRCUIT_WARMUP || circuit == CIRCUIT_STRETCH))

    companion object {
        /** The program as shipped. Also the target of "Restore defaults". */
        val DEFAULT = ProgramRules()

        fun circuitsCsv(list: List<Int>): String = list.joinToString(",")

        fun parseCircuitsCsv(csv: String): List<Int> =
            csv.split(",").mapNotNull { it.trim().toIntOrNull() }
    }
}
