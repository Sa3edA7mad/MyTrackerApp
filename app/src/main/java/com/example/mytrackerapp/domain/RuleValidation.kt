package com.example.mytrackerapp.domain

/**
 * Rule edits are validated before they can be saved, and analysed before they can be applied
 * to a running cycle. Both are pure so the editor screen can show consequences live.
 */
object RuleValidation {

    const val MAX_WEEKS = 26
    const val MAX_DAYS_PER_WEEK = 7
    const val MAX_CIRCUITS_PER_DAY = 20
    const val MAX_EXERCISES_PER_CIRCUIT = 40

    /** Empty list means the rules are saveable. Messages are user-facing. */
    fun validate(rules: ProgramRules): List<String> {
        val errors = mutableListOf<String>()
        if (rules.weeks !in 1..MAX_WEEKS) {
            errors += "Weeks must be between 1 and $MAX_WEEKS."
        }
        if (rules.daysPerWeek !in 1..MAX_DAYS_PER_WEEK) {
            errors += "Days per week must be between 1 and $MAX_DAYS_PER_WEEK."
        }
        if (rules.circuitsPerWeek.size != rules.weeks) {
            errors += "The program is ${rules.weeks} weeks but ${rules.circuitsPerWeek.size} " +
                "circuit counts are set."
        }
        if (rules.circuitsPerWeek.any { it !in 1..MAX_CIRCUITS_PER_DAY }) {
            errors += "Every week needs between 1 and $MAX_CIRCUITS_PER_DAY circuits a day."
        }
        if (rules.exercisesPerCircuit !in 1..MAX_EXERCISES_PER_CIRCUIT) {
            errors += "A circuit needs between 1 and $MAX_EXERCISES_PER_CIRCUIT exercises. " +
                "Turn at least one program exercise back on."
        }
        if (rules.dayRolloverHour !in 0..23) {
            errors += "The day rollover hour must be between 0 and 23."
        }
        if (rules.countRoutinesInTotals && !rules.warmUpEnabled && !rules.stretchEnabled) {
            errors += "Warm-up and stretch are both off, so there is nothing for " +
                "\"count them in totals\" to count."
        }
        return errors
    }
}

/**
 * What applying [to] to a cycle that is currently running under [from] would do.
 *
 * Shown in the confirmation dialog. Nothing here deletes anything — [orphanedCompletions]
 * rows stay in the table and stop counting (INVARIANT 8).
 */
data class ApplyImpact(
    val oldTotal: Int,
    val newTotal: Int,
    val oldDays: Int,
    val newDays: Int,
    /** Days that were settled under the old rules and would stop being settled. */
    val daysReopened: Int,
    /** Completion rows that would no longer sit in a slot the rules allow. */
    val orphanedCompletions: Int,
    /** True when the "you are here" marker would move backwards in the program. */
    val positionMovesBack: Boolean
) {
    val isLossless: Boolean get() = daysReopened == 0 && orphanedCompletions == 0
}

object RuleImpact {

    /**
     * @param completionSlots one entry per completion row: (week, day, circuit).
     */
    fun analyse(
        from: ProgramRules,
        to: ProgramRules,
        completionSlots: List<Triple<Int, Int, Int>>,
        closedPositions: Set<Position>
    ): ApplyImpact {
        fun counts(rules: ProgramRules): Map<Position, Int> = completionSlots
            .filter { (w, d, c) -> rules.isValidSlot(w, d, c) && rules.countsProgramSlot(c) }
            .groupingBy { (w, d, _) -> Position(w, d) }
            .eachCount()

        val oldCounts = counts(from)
        val newCounts = counts(to)

        val settledBefore = from.allPositions.filter {
            from.isDaySettled(it.week, oldCounts[it] ?: 0, it in closedPositions)
        }.toSet()
        val settledAfter = to.allPositions.filter {
            to.isDaySettled(it.week, newCounts[it] ?: 0, it in closedPositions)
        }.toSet()

        val oldIndex = from.nextPosition(oldCounts, closedPositions)
            ?.let { from.allPositions.indexOf(it) } ?: from.allPositions.size
        val newIndex = to.nextPosition(newCounts, closedPositions)
            ?.let { to.allPositions.indexOf(it) } ?: to.allPositions.size

        return ApplyImpact(
            oldTotal = from.totalExercisesInCycle(),
            newTotal = to.totalExercisesInCycle(),
            oldDays = from.allPositions.size,
            newDays = to.allPositions.size,
            daysReopened = settledBefore.count { it !in settledAfter },
            orphanedCompletions = completionSlots.count { (w, d, c) -> !to.isValidSlot(w, d, c) },
            positionMovesBack = newIndex < oldIndex
        )
    }
}
