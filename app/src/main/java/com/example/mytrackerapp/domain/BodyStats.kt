package com.example.mytrackerapp.domain

/** One canonical reading (kg/cm/percent) at a point in time. */
data class MetricPoint(val takenAt: Long, val value: Double)

data class MetricTrend(
    val metricId: String,
    val latest: Double?,
    val previous: Double?,
    val first: Double?,
    /** latest - previous. Null until there are two readings. */
    val deltaFromPrevious: Double?,
    val deltaFromFirst: Double?,
    /** Ascending by [MetricPoint.takenAt], most recent [limit] only. */
    val points: List<MetricPoint>
)

/** Null whenever an input is missing or a height of zero would divide by zero — never guess. */
fun bmi(weightKg: Double?, heightCm: Double?): Double? {
    if (weightKg == null || heightCm == null || heightCm <= 0.0) return null
    val heightM = heightCm / 100.0
    return weightKg / (heightM * heightM)
}

fun waistToHip(waistCm: Double?, hipCm: Double?): Double? {
    if (waistCm == null || hipCm == null || hipCm <= 0.0) return null
    return waistCm / hipCm
}

fun waistToHeight(waistCm: Double?, heightCm: Double?): Double? {
    if (waistCm == null || heightCm == null || heightCm <= 0.0) return null
    return waistCm / heightCm
}

fun leanMassKg(weightKg: Double?, bodyFatPercent: Double?): Double? {
    if (weightKg == null || bodyFatPercent == null) return null
    return weightKg * (1 - bodyFatPercent / 100.0)
}

/** Builds a [MetricTrend] from ascending-by-time [points], keeping the most recent [limit]. */
fun trendFor(metricId: String, points: List<MetricPoint>, limit: Int = 24): MetricTrend {
    val sorted = points.sortedBy { it.takenAt }
    val kept = sorted.takeLast(limit)
    val latest = kept.lastOrNull()?.value
    val previous = if (kept.size >= 2) kept[kept.size - 2].value else null
    val first = kept.firstOrNull()?.value
    return MetricTrend(
        metricId = metricId,
        latest = latest,
        previous = previous,
        first = first,
        deltaFromPrevious = if (latest != null && previous != null) latest - previous else null,
        deltaFromFirst = if (latest != null && first != null && kept.size >= 2) latest - first else null,
        points = kept
    )
}
