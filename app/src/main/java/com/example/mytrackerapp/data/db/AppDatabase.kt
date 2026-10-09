package com.example.mytrackerapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mytrackerapp.data.entity.CircuitResultEntity
import com.example.mytrackerapp.data.entity.CompletionEntity
import com.example.mytrackerapp.data.entity.CycleEntity
import com.example.mytrackerapp.data.entity.CycleRulesEntity
import com.example.mytrackerapp.data.entity.DayEntity
import com.example.mytrackerapp.data.entity.ExerciseEntity
import com.example.mytrackerapp.data.entity.MeasurementEntity
import com.example.mytrackerapp.data.entity.MetricEntity
import com.example.mytrackerapp.data.entity.ProgramEntity
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
        CycleRulesEntity::class,
        MetricEntity::class,
        MeasurementEntity::class,
        ProgramEntity::class,
        CircuitResultEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun cycleDao(): CycleDao
    abstract fun dayDao(): DayDao
    abstract fun completionDao(): CompletionDao
    abstract fun rulesDao(): RulesDao
    abstract fun metricDao(): MetricDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun programDao(): ProgramDao
    abstract fun resultDao(): CircuitResultDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}

/**
 * Seeds the catalog (29 Home rows + the library import), the Home program and the starter
 * programs, and opens Home's first cycle.
 *
 * Raw SQL rather than the DAOs because onCreate runs while the database instance is
 * still being constructed — there is nothing to call a DAO on yet.
 */
internal object SeedCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        SeedData.CATALOG.forEach { insertExercise(db, it, orIgnore = false) }

        // INVARIANT 1: there is always exactly one active cycle. Without this the very
        // first launch has no cycle to read and every screen renders empty.
        db.execSQL(
            "INSERT INTO cycles (startedAt, completedAt, isActive, programId) VALUES (?, NULL, 1, 1)",
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
                updatedAt, planText)
               VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, 'KG', 'CM', ?, '')""",
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
                countRoutinesInTotals, lockFutureDays, programExerciseIdsCsv, snapshotAt, planText)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')""",
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

        // INVARIANT 9: mirrors what MIGRATION_6_7 inserts for an upgrading install.
        insertPrograms(db, weightUnit = "KG", lengthUnit = "CM", now = now)

        // INVARIANT 9: mirrors what MIGRATION_4_5 inserts for an upgrading install.
        SeedData.DEFAULT_METRICS.forEach { m ->
            db.execSQL(
                """INSERT INTO metrics
                   (id, name, kind, hint, enabled, isCustom, decimals, sortOrder, archivedAt)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)""",
                arrayOf<Any>(
                    m.id,
                    m.name,
                    m.kind,
                    m.hint,
                    if (m.enabled) 1 else 0,
                    if (m.isCustom) 1 else 0,
                    m.decimals,
                    m.sortOrder
                )
            )
        }
    }
}
