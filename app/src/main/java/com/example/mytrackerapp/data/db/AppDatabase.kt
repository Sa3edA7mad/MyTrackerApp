package com.example.mytrackerapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mytrackerapp.data.entity.CompletionEntity
import com.example.mytrackerapp.data.entity.CycleEntity
import com.example.mytrackerapp.data.entity.CycleRulesEntity
import com.example.mytrackerapp.data.entity.DayEntity
import com.example.mytrackerapp.data.entity.ExerciseEntity
import com.example.mytrackerapp.data.entity.ProgramRulesEntity
import com.example.mytrackerapp.data.seed.SeedData
import com.example.mytrackerapp.domain.ProgramRules

@Database(
    entities = [
        ExerciseEntity::class,
        CycleEntity::class,
        DayEntity::class,
        CompletionEntity::class,
        ProgramRulesEntity::class,
        CycleRulesEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun cycleDao(): CycleDao
    abstract fun dayDao(): DayDao
    abstract fun completionDao(): CompletionDao
    abstract fun rulesDao(): RulesDao

    companion object {
        const val NAME = "tracker.db"

        /**
         * INVARIANT 6: fallbackToDestructiveMigration() is forbidden in this codebase.
         * This database holds training history that cannot be regenerated, so any
         * schema change must ship a real Migration. The absence of that call here is
         * deliberate and is checked by a grep gate.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}

/**
 * Seeds the 29-row catalog and opens the first cycle.
 *
 * Raw SQL rather than the DAOs because onCreate runs while the database instance is
 * still being constructed — there is nothing to call a DAO on yet.
 */
internal object SeedCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        SeedData.ALL_EXERCISES.forEach { e ->
            db.execSQL(
                """INSERT INTO exercises
                   (id, name, category, muscles, instructions, targetType, targetValue,
                    perSide, targetLabel, videoUrl, sortOrder, slot, enabled, archivedAt,
                    isCustom, tracksReps, tracksLoad, defaultLoadKg, defaultBandLevel,
                    progressionStep)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, NULL, NULL, ?)""",
                arrayOf<Any>(
                    e.id,
                    e.name,
                    e.category,
                    e.muscles,
                    e.instructions,
                    e.targetType,
                    e.targetValue,
                    if (e.perSide) 1 else 0,
                    e.targetLabel,
                    e.videoUrl,
                    e.sortOrder,
                    e.slot,
                    if (e.enabled) 1 else 0,
                    if (e.isCustom) 1 else 0,
                    if (e.tracksReps) 1 else 0,
                    if (e.tracksLoad) 1 else 0,
                    e.progressionStep
                )
            )
        }

        // INVARIANT 1: there is always exactly one active cycle. Without this the very
        // first launch has no cycle to read and every screen renders empty.
        db.execSQL(
            "INSERT INTO cycles (startedAt, completedAt, isActive) VALUES (?, NULL, 1)",
            arrayOf(System.currentTimeMillis())
        )
        val cycleId = db.compileStatement("SELECT last_insert_rowid()").use {
            it.simpleQueryForLong()
        }

        val now = System.currentTimeMillis()
        val default = ProgramRules.DEFAULT

        // INVARIANT 9: this must seed the same program_rules/cycle_rules content that
        // MIGRATION_1_2 backfills for an upgrading install.
        db.execSQL(
            """INSERT INTO program_rules
               (id, weeks, daysPerWeek, circuitsPerWeekCsv, dayRolloverHour, warmUpEnabled,
                stretchEnabled, countRoutinesInTotals, lockFutureDays, weightUnit, lengthUnit,
                updatedAt)
               VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, 'KG', 'CM', ?)""",
            arrayOf<Any>(
                default.weeks,
                default.daysPerWeek,
                ProgramRules.circuitsCsv(default.circuitsPerWeek),
                default.dayRolloverHour,
                if (default.warmUpEnabled) 1 else 0,
                if (default.stretchEnabled) 1 else 0,
                if (default.countRoutinesInTotals) 1 else 0,
                if (default.lockFutureDays) 1 else 0,
                now
            )
        )

        val programIds = SeedData.ALL_EXERCISES
            .filter { it.category == "BODYWEIGHT" || it.category == "BAND" }
            .sortedBy { it.sortOrder }
            .joinToString(",") { it.id }

        db.execSQL(
            """INSERT INTO cycle_rules
               (cycleId, weeks, daysPerWeek, circuitsPerWeekCsv, exercisesPerCircuit,
                warmUpCount, stretchCount, dayRolloverHour, warmUpEnabled, stretchEnabled,
                countRoutinesInTotals, lockFutureDays, programExerciseIdsCsv, snapshotAt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf<Any>(
                cycleId,
                default.weeks,
                default.daysPerWeek,
                ProgramRules.circuitsCsv(default.circuitsPerWeek),
                default.exercisesPerCircuit,
                default.warmUpCount,
                default.stretchCount,
                default.dayRolloverHour,
                if (default.warmUpEnabled) 1 else 0,
                if (default.stretchEnabled) 1 else 0,
                if (default.countRoutinesInTotals) 1 else 0,
                if (default.lockFutureDays) 1 else 0,
                programIds,
                now
            )
        )
    }
}
