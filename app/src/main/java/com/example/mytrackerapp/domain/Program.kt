package com.example.mytrackerapp.domain

import com.example.mytrackerapp.domain.model.DayTally
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure program math that is not part of [ProgramRules] — position bookkeeping, calendar
 * rollover and streak/history math. No Android imports — everything here is unit-tested on
 * the JVM.
 *
 * The shape of the program itself (weeks, days, circuits, exercises per circuit) lives in
 * [ProgramRules] and is editable at runtime; this file only holds what does not vary per rule
 * set.
 */

/**
 * Sentinel circuit indices for the once-a-day routines.
 *
 * INVARIANT 2: program totals only ever count `circuit >= 1` unless
 * [ProgramRules.countRoutinesInTotals] is on. Warm-up and stretch completions are stored so a
 * half-finished routine resumes, but by default they never appear in a day, week, or cycle
 * total.
 */
const val CIRCUIT_WARMUP = 0
const val CIRCUIT_STRETCH = -1

/** Default day rollover hour: a set finished at 01:00 belongs to the day that just ended. */
const val DAY_ROLLOVER_HOUR = 4L

data class Position(val week: Int, val day: Int)

/** Local calendar date a timestamp belongs to, with the day-rollover hour applied. */
fun trainingDate(
    epochMs: Long,
    rolloverHour: Int = DAY_ROLLOVER_HOUR.toInt(),
    zone: ZoneId = ZoneId.systemDefault()
): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(zone).minusHours(rolloverHour.toLong()).toLocalDate()

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
    .groupingBy { trainingDate(it, zone = zone) }
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
