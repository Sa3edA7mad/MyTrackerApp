package com.example.mytrackerapp.domain.model

enum class MetricKind { WEIGHT, LENGTH, PERCENT, COUNT }

data class Metric(
    val id: String,
    val name: String,
    val kind: MetricKind,
    val hint: String,
    val enabled: Boolean,
    val isCustom: Boolean,
    val decimals: Int,
    val sortOrder: Int,
    val archivedAt: Long? = null
) {
    val isArchived: Boolean get() = archivedAt != null
}

data class MeasurementEntry(
    val id: Long,
    val metricId: String,
    /** Canonical value: kilograms/centimetres/percent, never a display unit. */
    val value: Double,
    val takenAt: Long,
    val note: String
)

/** Derived body stats shown on the measurements screen. Null fields name what's missing. */
data class DerivedBodyStats(
    val bmi: Double?,
    val waistToHip: Double?,
    val waistToHeight: Double?,
    val leanMassKg: Double?
)
