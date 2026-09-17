package com.example.mytrackerapp.domain

import java.time.LocalDate
import java.time.ZoneId

/** One completed set's optional detail, as logged (or not) at the time. */
data class SetLog(
    val completedAt: Long,
    val reps: Int?,
    val loadKg: Double?,
    val holdSeconds: Int?,
    val rpe: Int?
)

data class DayVolume(val date: LocalDate, val volumeKg: Double, val sets: Int)

data class PerformanceSummary(
    val sets: Int,
    val loggedSets: Int,
    val bestLoadKg: Double?,
    val bestReps: Int?,
    val bestHoldSeconds: Int?,
    /** Sum of reps x load over all logged sets, in kilograms. Null when nothing has a load. */
    val totalVolumeKg: Double?,
    /** Per training date, ascending, most recent [DayVolume]s only. */
    val volumeByDay: List<DayVolume>,
    /** Signed percentage change in best load between the first and last training date. */
    val loadTrendPercent: Int?
)

/**
 * Summarises a set of [SetLog]s for the exercise detail screen's performance section.
 * Pure and unit-tested — no Android imports.
 */
fun summarise(
    logs: List<SetLog>,
    rolloverHour: Int = 4,
    limit: Int = 12,
    zone: ZoneId = ZoneId.systemDefault()
): PerformanceSummary {
    val logged = logs.filter { it.reps != null || it.loadKg != null || it.holdSeconds != null }

    val bestLoadKg = logged.mapNotNull { it.loadKg }.maxOrNull()
    val bestReps = logged.mapNotNull { it.reps }.maxOrNull()
    val bestHoldSeconds = logged.mapNotNull { it.holdSeconds }.maxOrNull()

    val volumeByDate: Map<LocalDate, DayVolume> = logged
        .filter { it.reps != null && it.loadKg != null }
        .groupBy { trainingDate(it.completedAt, rolloverHour, zone) }
        .mapValues { (date, rows) ->
            DayVolume(date = date, volumeKg = rows.sumOf { it.reps!! * it.loadKg!! }, sets = rows.size)
        }
    val volumeByDay = volumeByDate.values.sortedBy { it.date }.takeLast(limit)
    val totalVolumeKg = if (volumeByDate.isEmpty()) null else volumeByDate.values.sumOf { it.volumeKg }

    val loadsByDate = logged.filter { it.loadKg != null }
        .groupBy { trainingDate(it.completedAt, rolloverHour, zone) }
        .mapValues { (_, rows) -> rows.mapNotNull { it.loadKg }.max() }
        .toList()
        .sortedBy { it.first }

    val loadTrendPercent = if (loadsByDate.size >= 2) {
        val first = loadsByDate.first().second
        val last = loadsByDate.last().second
        if (first == 0.0) null else (((last - first) / first) * 100).toInt()
    } else {
        null
    }

    return PerformanceSummary(
        sets = logs.size,
        loggedSets = logged.size,
        bestLoadKg = bestLoadKg,
        bestReps = bestReps,
        bestHoldSeconds = bestHoldSeconds,
        totalVolumeKg = totalVolumeKg,
        volumeByDay = volumeByDay,
        loadTrendPercent = loadTrendPercent
    )
}
