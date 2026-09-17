package com.example.mytrackerapp.domain

import com.example.mytrackerapp.domain.model.DayTally
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure program math. No Android imports — everything here is unit-tested on the JVM.
 *
 * All of it derives from the source workbook (Saeed_4Week.xlsx): 4 weeks, 6 days a
 * week, 13 exercises per circuit, and a circuit count that ramps 4 -> 5 -> 6 -> 7.
 */

const val WEEKS = 4
const val DAYS_PER_WEEK = 6
const val EXERCISES_PER_CIRCUIT = 13

/**
 * Sentinel circuit indices for the once-a-day routines.
 *
 * INVARIANT 2: program totals only ever count `circuit >= 1`. Warm-up and stretch
 * completions are stored so a half-finished routine resumes, but they must never
 * appear in a day, week, or cycle total.
 */
const val CIRCUIT_WARMUP = 0
const val CIRCUIT_STRETCH = -1

/** A set finished at 01:00 belongs to the day that just ended, not the new one. */
const val DAY_ROLLOVER_HOUR = 4L

/**
 * From the workbook. Stored as data rather than computed as `week + 3`: the ramp is
 * a programming decision by whoever wrote the plan, not an arithmetic law.
 */
private val CIRCUITS_PER_WEEK = intArrayOf(4, 5, 6, 7)

fun circuitsForWeek(week: Int): Int {
    require(week in 1..WEEKS) { "week must be 1..$WEEKS, was $week" }
    return CIRCUITS_PER_WEEK[week - 1]
}

/** 52 / 65 / 78 / 91 */
fun exercisesPerDay(week: Int): Int = circuitsForWeek(week) * EXERCISES_PER_CIRCUIT

/** 312 / 390 / 468 / 546 */
fun exercisesForWeek(week: Int): Int = exercisesPerDay(week) * DAYS_PER_WEEK

/** 1716 */
fun totalExercisesInCycle(): Int = (1..WEEKS).sumOf { exercisesForWeek(it) }

/** 132 */
fun totalCircuitsInCycle(): Int = (1..WEEKS).sumOf { circuitsForWeek(it) * DAYS_PER_WEEK }

data class Position(val week: Int, val day: Int)

/** All 24 training days in program order. */
val ALL_POSITIONS: List<Position> =
    (1..WEEKS).flatMap { w -> (1..DAYS_PER_WEEK).map { d -> Position(w, d) } }

/**
 * INVARIANT 3: a day is settled when it is fully complete AND stretched, OR the user
 * closed it early.
 *
 * Without the closed-early escape a partly-finished day traps the counter forever and
 * the cycle can never reach its completion screen.
 *
 * `stretchDone` is required alongside the exercise count, not just carried for display.
 * Without it, the day settles the instant the last circuit exercise is ticked, and the
 * counter jumps to the next day before the stretch routine is ever reachable — orphaning
 * it, since RoutineScreen always resolves to the *current* day, never a past one. This was
 * caught live: after finishing all 4 circuits of Week 1 Day 1, Today jumped straight to
 * Day 2 without ever offering the stretch screen.
 */
fun isDaySettled(week: Int, doneCount: Int, stretchDone: Boolean, closed: Boolean): Boolean =
    closed || (doneCount >= exercisesPerDay(week) && stretchDone)

/**
 * First unsettled day in program order, or null when the cycle is finished.
 *
 * @param doneByPosition completion counts for `circuit >= 1` only (INVARIANT 2).
 * @param stretchDonePositions days whose stretch routine has been completed.
 */
fun nextPosition(
    doneByPosition: Map<Position, Int>,
    stretchDonePositions: Set<Position>,
    closedPositions: Set<Position>
): Position? = ALL_POSITIONS.firstOrNull { p ->
    !isDaySettled(p.week, doneByPosition[p] ?: 0, p in stretchDonePositions, p in closedPositions)
}

/** Local calendar date a timestamp belongs to, with the 4am rollover applied. */
fun trainingDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(zone).minusHours(DAY_ROLLOVER_HOUR).toLocalDate()

/**
 * Completion counts bucketed by training date, most recent [limit] dates, ascending.
 *
 * Drives the sparkline on the exercise detail screen. Dates with no work simply do not
 * appear — this is "your last N training days", not "the last N calendar days", which for
 * a 6-day-a-week program would otherwise be mostly empty columns.
 */
fun recentTallies(
    completionTimes: List<Long>,
    limit: Int = 7,
    zone: ZoneId = ZoneId.systemDefault()
): List<DayTally> = completionTimes
    .groupingBy { trainingDate(it, zone) }
    .eachCount()
    .toList()
    .sortedBy { it.first }
    .takeLast(limit)
    .map { DayTally(date = it.first, count = it.second) }

/**
 * Longest run of consecutive training dates anywhere in the set.
 *
 * Distinct from [streakDays], which only counts the run that is still alive. The cycle
 * summary wants the best you managed, not what survives today.
 */
fun longestStreak(trainingDates: Set<LocalDate>): Int {
    if (trainingDates.isEmpty()) return 0
    val sorted = trainingDates.sorted()
    var best = 1
    var run = 1
    for (i in 1 until sorted.size) {
        run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
        if (run > best) best = run
    }
    return best
}

/**
 * Consecutive training dates ending today or yesterday.
 *
 * Returns 0 when the most recent training date is older than yesterday, so a broken
 * streak reads 0 rather than showing a stale number from last week.
 */
fun streakDays(trainingDates: Set<LocalDate>, today: LocalDate): Int {
    var cursor = when {
        today in trainingDates -> today
        today.minusDays(1) in trainingDates -> today.minusDays(1)
        else -> return 0
    }
    var n = 0
    while (cursor in trainingDates) {
        n++
        cursor = cursor.minusDays(1)
    }
    return n
}
