package com.example.mytrackerapp.repo

import com.example.mytrackerapp.data.db.MeasurementDao
import com.example.mytrackerapp.data.db.MetricDao
import com.example.mytrackerapp.data.entity.MeasurementEntity
import com.example.mytrackerapp.data.entity.MetricEntity
import com.example.mytrackerapp.domain.LengthUnit
import com.example.mytrackerapp.domain.MetricPoint
import com.example.mytrackerapp.domain.MetricTrend
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.Units
import com.example.mytrackerapp.domain.WeightUnit
import com.example.mytrackerapp.domain.bmi
import com.example.mytrackerapp.domain.leanMassKg
import com.example.mytrackerapp.domain.model.DerivedBodyStats
import com.example.mytrackerapp.domain.model.MeasurementEntry
import com.example.mytrackerapp.domain.model.Metric
import com.example.mytrackerapp.domain.model.MetricKind
import com.example.mytrackerapp.domain.trendFor
import com.example.mytrackerapp.domain.waistToHeight
import com.example.mytrackerapp.domain.waistToHip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

fun MetricEntity.toDomain(): Metric = Metric(
    id = id,
    name = name,
    kind = runCatching { MetricKind.valueOf(kind) }.getOrDefault(MetricKind.COUNT),
    hint = hint,
    enabled = enabled,
    isCustom = isCustom,
    decimals = decimals,
    sortOrder = sortOrder,
    archivedAt = archivedAt
)

fun MeasurementEntity.toDomain(): MeasurementEntry =
    MeasurementEntry(id = id, metricId = metricId, value = value, takenAt = takenAt, note = note)

private const val WEIGHT_METRIC_ID = "bodyweight"
private const val HEIGHT_METRIC_ID = "height"
private const val WAIST_METRIC_ID = "waist"
private const val HIP_METRIC_ID = "hips"
private const val BODY_FAT_METRIC_ID = "body_fat"

/**
 * Body measurement read/write. Values are always stored canonically (kg/cm/percent);
 * [log] is the only place a display-unit value gets converted, and only on the way in.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MeasurementRepository(
    private val metrics: MetricDao,
    private val measurements: MeasurementDao,
    private val unitPrefs: () -> Flow<UnitPrefs>
) {

    fun observeMetrics(includeDisabled: Boolean = false): Flow<List<Metric>> =
        (if (includeDisabled) metrics.observeAll() else metrics.observeEnabled())
            .map { list -> list.map { it.toDomain() } }

    fun observeHistory(metricId: String): Flow<List<MeasurementEntry>> =
        measurements.observeHistory(metricId).map { list -> list.map { it.toDomain() } }

    fun observeTrends(): Flow<List<MetricTrend>> =
        metrics.observeEnabled().flatMapLatest { enabled ->
            if (enabled.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(enabled.map { metric -> observeTrend(metric.id) }) { it.toList() }
            }
        }

    private fun observeTrend(metricId: String): Flow<MetricTrend> =
        measurements.observeHistory(metricId).map { rows ->
            trendFor(metricId, rows.map { MetricPoint(it.takenAt, it.value) })
        }

    fun observeDerived(): Flow<DerivedBodyStats> = combine(
        latestValue(WEIGHT_METRIC_ID),
        latestValue(HEIGHT_METRIC_ID),
        latestValue(WAIST_METRIC_ID),
        latestValue(HIP_METRIC_ID),
        latestValue(BODY_FAT_METRIC_ID)
    ) { weight, height, waist, hip, bodyFat ->
        DerivedBodyStats(
            bmi = bmi(weight, height),
            waistToHip = waistToHip(waist, hip),
            waistToHeight = waistToHeight(waist, height),
            leanMassKg = leanMassKg(weight, bodyFat)
        )
    }

    private fun latestValue(metricId: String): Flow<Double?> =
        measurements.observeLatest(metricId).map { it?.value }

    /** [displayValue] is in the unit [unitPrefs] currently reports; converted to canonical here. */
    suspend fun log(
        metricId: String,
        displayValue: Double,
        takenAt: Long,
        note: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val metric = metrics.getById(metricId)
            ?: return@withContext Result.failure(NoSuchElementException("No metric $metricId"))
        val canonical = toCanonical(metric, displayValue)
        val errors = validate(metric, canonical)
        if (errors != null) return@withContext Result.failure(IllegalArgumentException(errors))

        measurements.insert(MeasurementEntity(metricId = metricId, value = canonical, takenAt = takenAt, note = note))
        Result.success(Unit)
    }

    suspend fun updateEntry(id: Long, displayValue: Double, note: String, metricId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val metric = metrics.getById(metricId)
                ?: return@withContext Result.failure(NoSuchElementException("No metric $metricId"))
            val canonical = toCanonical(metric, displayValue)
            val errors = validate(metric, canonical)
            if (errors != null) return@withContext Result.failure(IllegalArgumentException(errors))

            measurements.update(id, canonical, note)
            Result.success(Unit)
        }

    /** Canonical -> current display unit, for showing an existing reading back for editing. */
    suspend fun toDisplayValue(metric: Metric, canonicalValue: Double): Double =
        unitPrefs().first().toDisplay(metric.kind, canonicalValue)

    /** The one hard delete in the app — a typo'd measurement has no historical value. */
    suspend fun deleteEntry(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        measurements.delete(id)
        Result.success(Unit)
    }

    suspend fun setMetricEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        metrics.setEnabled(id, enabled)
    }

    suspend fun createMetric(name: String, kind: MetricKind, decimals: Int, hint: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (name.isBlank()) return@withContext Result.failure(IllegalArgumentException("Name can't be empty."))
            val id = slugify(name)
            val maxSort = metrics.observeAll().first().maxOfOrNull { it.sortOrder } ?: 0
            metrics.upsert(
                MetricEntity(
                    id = id,
                    name = name,
                    kind = kind.name,
                    hint = hint,
                    enabled = true,
                    isCustom = true,
                    decimals = decimals,
                    sortOrder = maxSort + 1
                )
            )
            Result.success(id)
        }

    /** Soft delete; readings are kept. */
    suspend fun archiveMetric(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        metrics.getById(id) ?: return@withContext Result.failure(NoSuchElementException("No metric $id"))
        metrics.archive(id, System.currentTimeMillis())
        Result.success(Unit)
    }

    private suspend fun toCanonical(metric: MetricEntity, displayValue: Double): Double {
        val kind = runCatching { MetricKind.valueOf(metric.kind) }.getOrDefault(MetricKind.COUNT)
        return unitPrefs().first().toCanonical(kind, displayValue)
    }

    private fun validate(metric: MetricEntity, canonical: Double): String? = when {
        canonical <= 0.0 && metric.kind != "COUNT" -> "Value must be greater than zero."
        metric.kind == "PERCENT" && canonical !in 0.0..100.0 -> "Percent must be between 0 and 100."
        else -> null
    }

    private fun slugify(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "metric" }
}

/** Converts a display-unit value to canonical for a metric of the given [kind]. */
fun UnitPrefs.toCanonical(kind: MetricKind, displayValue: Double): Double = when (kind) {
    MetricKind.WEIGHT -> Units.displayToKg(displayValue, weight)
    MetricKind.LENGTH -> Units.displayToCm(displayValue, length)
    MetricKind.PERCENT, MetricKind.COUNT -> displayValue
}

/** Converts a canonical value to the current display unit for a metric of the given [kind]. */
fun UnitPrefs.toDisplay(kind: MetricKind, canonicalValue: Double): Double = when (kind) {
    MetricKind.WEIGHT -> Units.kgToDisplay(canonicalValue, weight)
    MetricKind.LENGTH -> Units.cmToDisplay(canonicalValue, length)
    MetricKind.PERCENT, MetricKind.COUNT -> canonicalValue
}
