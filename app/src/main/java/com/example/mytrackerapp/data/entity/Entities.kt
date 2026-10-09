package com.example.mytrackerapp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The exercise catalog. Seeded with 13 program exercises + 8 warm-up moves + 8 stretches,
 * plus the 123-row library import, on database create; editable at runtime.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** BODYWEIGHT | BAND | WARMUP | STRETCH | GYM | CROSSFIT | MOBILITY | CORE — the display badge only. */
    val category: String,
    /** "Legs · Glutes · Core"; empty for warm-up moves, which the sheet leaves blank. */
    val muscles: String,
    val instructions: String,
    /** REPS | SECONDS */
    val targetType: String,
    /** Drives the hold timer. 15 for Dead Hang, 45 for Child's Pose. */
    val targetValue: Int,
    /** True for "each side" moves, which run as a two-stage flow. */
    val perSide: Boolean,
    /** Verbatim from the sheet, e.g. "10 reps each side". Display only. */
    val targetLabel: String,
    val videoUrl: String,
    val sortOrder: Int,
    /** PROGRAM | WARMUP | STRETCH | LIBRARY. A circuit that is slot-backed (the Home program's)
     *  holds every enabled exercise in its slot; LIBRARY is in no slot list. */
    val slot: String = "PROGRAM",
    /** In the rotation or benched, without archiving. */
    val enabled: Boolean = true,
    /** Soft delete (INVARIANT 8's catalog equivalent). Archived rows never appear in a
     *  circuit or the Library, but their completion history stays readable. */
    val archivedAt: Long? = null,
    /** User-created. "Restore default catalog" re-seeds the originals and leaves these alone. */
    val isCustom: Boolean = false,
    val tracksReps: Boolean = false,
    val tracksLoad: Boolean = false,
    val defaultLoadKg: Double? = null,
    val defaultBandLevel: String? = null,
    /** Added to targetValue per week: week w targets targetValue + progressionStep * (w-1). */
    val progressionStep: Int = 0,
    /** Library metadata from the exercise workbook. Empty for rows that predate it. */
    val equipment: String = "",
    /** BEGINNER | INTERMEDIATE | ADVANCED, or empty. */
    val level: String = "",
    /** The one-line technique cue, shown as a highlighted tip. */
    val cue: String = "",
    val videoTitle: String = "",
    val videoChannel: String = ""
)

/**
 * A training program (Home, Gym, Conditioning…). Several can be active at once, each with its
 * own cycle. Its rules and circuit plan live in the `program_rules` row with the same id.
 * Programs are archived, never deleted, so their history stays readable.
 */
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Shown on Today. Pausing a program keeps its cycle exactly where it was. */
    val active: Boolean,
    val archivedAt: Long? = null,
    val createdAt: Long
)

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val completedAt: Long? = null,
    /** At most one active cycle per program (INVARIANT 1). */
    val isActive: Boolean = true,
    val programId: Long = 1
)

/**
 * Per-day state that cannot be derived from completions.
 *
 * There is deliberately no `completedAt`: day completion is always computed from the
 * completion count (INVARIANT 5), so it cannot drift. [closedAt] is different — it
 * records a decision the user made, and is the only escape from a partly-done day
 * (INVARIANT 3).
 */
@Entity(
    tableName = "days",
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cycleId", "week", "day"], unique = true)]
)
data class DayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val week: Int,
    val day: Int,
    val warmUpDoneAt: Long? = null,
    val stretchDoneAt: Long? = null,
    val closedAt: Long? = null
)

/**
 * One row per completed exercise. Un-ticking DELETES the row — presence of the row is
 * the truth, which makes every aggregate a COUNT(*).
 *
 * The unique index plus OnConflictStrategy.IGNORE is what makes a double-tap safe.
 */
@Entity(
    tableName = "completions",
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cycleId", "week", "day", "circuit", "exerciseId", "setNumber"], unique = true),
        Index(value = ["exerciseId"])
    ]
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val week: Int,
    val day: Int,
    /** >= 1 program circuit | 0 warm-up | -1 stretch. See INVARIANT 2. */
    val circuit: Int,
    val exerciseId: String,
    val completedAt: Long,
    /**
     * Optional set detail. All nullable and never backfilled (INVARIANT 5 stays load-bearing:
     * every aggregate is a `COUNT(*)` and must keep working whether or not these are set).
     * NULL means "not logged", which is the honest value for every row recorded before this
     * feature existed and for any set where logging is off or skipped.
     */
    val reps: Int? = null,
    /** Always kilograms — the unit setting converts only at display (see domain/Units.kt). */
    val loadKg: Double? = null,
    val bandLevel: String? = null,
    val holdSeconds: Int? = null,
    /** 1-10 perceived effort. */
    val rpe: Int? = null,
    val note: String? = null,
    /** 1-based set within the circuit. Every row from before sets existed is set 1. */
    val setNumber: Int = 1
)

/**
 * The recorded result of a timed circuit: rounds for AMRAP, seconds for FOR_TIME.
 * One per circuit slot; re-recording replaces it.
 */
@Entity(
    tableName = "circuit_results",
    primaryKeys = ["cycleId", "week", "day", "circuit"],
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CircuitResultEntity(
    val cycleId: Long,
    val week: Int,
    val day: Int,
    val circuit: Int,
    val value: Int,
    val recordedAt: Long
)

/**
 * A program's editable rule set; [id] is the program's id (1 = the original Home program).
 * This is the *draft/default*: a running cycle reads its own [CycleRulesEntity] snapshot
 * instead (INVARIANT 7).
 */
@Entity(tableName = "program_rules")
data class ProgramRulesEntity(
    @PrimaryKey val id: Int = 1,
    val weeks: Int,
    val daysPerWeek: Int,
    /** Comma-separated, one entry per week, e.g. "4,5,6,7". */
    val circuitsPerWeekCsv: String,
    val dayRolloverHour: Int,
    val warmUpEnabled: Boolean,
    val stretchEnabled: Boolean,
    val countRoutinesInTotals: Boolean,
    val lockFutureDays: Boolean,
    /** KG | LB — display only; loads are always stored in kilograms. */
    val weightUnit: String,
    /** CM | IN — display only; girths are always stored in centimetres. */
    val lengthUnit: String,
    val updatedAt: Long,
    /** The program's circuits, day rotation, warm-up and stretch, as [com.example.mytrackerapp.domain.PlanCodec]
     *  text. Blank means the original catalog-slot plan (Home before programs existed). */
    val planText: String = ""
)

/**
 * INVARIANT 7: the rules a cycle started under, frozen. Reads for that cycle use this row.
 *
 * [programExerciseIdsCsv] freezes the circuit's composition too, so archiving an exercise
 * mid-cycle cannot retroactively change what a completed circuit meant.
 */
@Entity(
    tableName = "cycle_rules",
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CycleRulesEntity(
    @PrimaryKey val cycleId: Long,
    val weeks: Int,
    val daysPerWeek: Int,
    val circuitsPerWeekCsv: String,
    val exercisesPerCircuit: Int,
    val warmUpCount: Int,
    val stretchCount: Int,
    val dayRolloverHour: Int,
    val warmUpEnabled: Boolean,
    val stretchEnabled: Boolean,
    val countRoutinesInTotals: Boolean,
    val lockFutureDays: Boolean,
    /** Ordered exercise ids that made up a circuit when this snapshot was taken. */
    val programExerciseIdsCsv: String,
    val snapshotAt: Long,
    /** The resolved circuit plan frozen with this snapshot. Blank for cycles that predate
     *  programs, which read as one circuit of [programExerciseIdsCsv]. */
    val planText: String = ""
)

/** The metric catalog is itself an editable rule: enable, disable, rename, add your own. */
@Entity(tableName = "metrics")
data class MetricEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** WEIGHT (kg) | LENGTH (cm) | PERCENT | COUNT */
    val kind: String,
    val hint: String,
    val enabled: Boolean,
    val isCustom: Boolean,
    val decimals: Int,
    val sortOrder: Int,
    val archivedAt: Long? = null
)

@Entity(
    tableName = "measurements",
    indices = [
        Index(value = ["metricId", "takenAt"], unique = true),
        Index(value = ["takenAt"])
    ]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metricId: String,
    /** Canonical: kilograms for WEIGHT, centimetres for LENGTH, percent for PERCENT. */
    val value: Double,
    val takenAt: Long,
    val note: String = ""
)
