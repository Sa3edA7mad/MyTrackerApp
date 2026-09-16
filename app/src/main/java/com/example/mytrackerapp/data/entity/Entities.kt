package com.example.mytrackerapp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Static catalog: 13 program exercises + 8 warm-up moves + 8 stretches = 29 rows.
 * Seeded once on database create and never mutated at runtime.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** BODYWEIGHT | BAND | WARMUP | STRETCH */
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
    val sortOrder: Int
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
