package com.example.mytrackerapp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The exercise catalog. Seeded with 13 program exercises + 8 warm-up moves + 8 stretches
 * on database create; editable at runtime from T14 onward (create/update/archive/reorder).
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** BODYWEIGHT | BAND | WARMUP | STRETCH — the display badge only. */
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
    /** PROGRAM | WARMUP | STRETCH. Decides circuit membership — see TrackerRepository. */
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
    val progressionStep: Int = 0
)

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val completedAt: Long? = null,
    val isActive: Boolean = true
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
        Index(value = ["cycleId", "week", "day", "circuit", "exerciseId"], unique = true),
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
    val completedAt: Long
)

/**
 * The single editable rule set, id is always 1. This is the *draft/default*: a running cycle
 * reads its own [CycleRulesEntity] snapshot instead (INVARIANT 7).
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
    val updatedAt: Long
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
    val snapshotAt: Long
)
