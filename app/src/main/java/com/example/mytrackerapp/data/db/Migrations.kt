package com.example.mytrackerapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mytrackerapp.data.seed.SeedData
import com.example.mytrackerapp.domain.ProgramRules

/**
 * v1 -> v2: adds `program_rules` (the single editable draft) and `cycle_rules` (the
 * per-cycle frozen snapshot, INVARIANT 7). Backfills both tables so upgrading installs get
 * the same content INVARIANT 9 requires [SeedCallback] to produce for a fresh install.
 *
 * The two `CREATE TABLE` statements are copied verbatim from the Room-generated
 * `app/schemas/.../2.json` rather than hand-typed, because a hand-typed statement that
 * differs from Room's own schema by so much as a default or a clause order fails Room's
 * migration validation at runtime.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `program_rules` (`id` INTEGER NOT NULL, `weeks` INTEGER NOT NULL, `daysPerWeek` INTEGER NOT NULL, `circuitsPerWeekCsv` TEXT NOT NULL, `dayRolloverHour` INTEGER NOT NULL, `warmUpEnabled` INTEGER NOT NULL, `stretchEnabled` INTEGER NOT NULL, `countRoutinesInTotals` INTEGER NOT NULL, `lockFutureDays` INTEGER NOT NULL, `weightUnit` TEXT NOT NULL, `lengthUnit` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cycle_rules` (`cycleId` INTEGER NOT NULL, `weeks` INTEGER NOT NULL, `daysPerWeek` INTEGER NOT NULL, `circuitsPerWeekCsv` TEXT NOT NULL, `exercisesPerCircuit` INTEGER NOT NULL, `warmUpCount` INTEGER NOT NULL, `stretchCount` INTEGER NOT NULL, `dayRolloverHour` INTEGER NOT NULL, `warmUpEnabled` INTEGER NOT NULL, `stretchEnabled` INTEGER NOT NULL, `countRoutinesInTotals` INTEGER NOT NULL, `lockFutureDays` INTEGER NOT NULL, `programExerciseIdsCsv` TEXT NOT NULL, `snapshotAt` INTEGER NOT NULL, PRIMARY KEY(`cycleId`), FOREIGN KEY(`cycleId`) REFERENCES `cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )

        val now = System.currentTimeMillis()
        val default = ProgramRules.DEFAULT
        val circuitsCsv = ProgramRules.circuitsCsv(default.circuitsPerWeek)

        db.execSQL(
            """INSERT OR IGNORE INTO program_rules
               (id, weeks, daysPerWeek, circuitsPerWeekCsv, dayRolloverHour, warmUpEnabled,
                stretchEnabled, countRoutinesInTotals, lockFutureDays, weightUnit, lengthUnit,
                updatedAt)
               VALUES (1, ${default.weeks}, ${default.daysPerWeek}, '$circuitsCsv',
                       ${default.dayRolloverHour}, ${if (default.warmUpEnabled) 1 else 0},
                       ${if (default.stretchEnabled) 1 else 0},
                       ${if (default.countRoutinesInTotals) 1 else 0},
                       ${if (default.lockFutureDays) 1 else 0}, 'KG', 'CM', $now)"""
        )

        val programIds = SeedData.ALL_EXERCISES
            .filter { it.category == "BODYWEIGHT" || it.category == "BAND" }
            .sortedBy { it.sortOrder }
            .joinToString(",") { it.id }

        // One cycle_rules row per existing cycle, using the shipped default shape — this is
        // the best information available for a cycle that predates rule snapshots.
        db.execSQL(
            """INSERT INTO cycle_rules
               (cycleId, weeks, daysPerWeek, circuitsPerWeekCsv, exercisesPerCircuit,
                warmUpCount, stretchCount, dayRolloverHour, warmUpEnabled, stretchEnabled,
                countRoutinesInTotals, lockFutureDays, programExerciseIdsCsv, snapshotAt)
               SELECT id, ${default.weeks}, ${default.daysPerWeek}, '$circuitsCsv',
                      ${default.exercisesPerCircuit}, ${default.warmUpCount},
                      ${default.stretchCount}, ${default.dayRolloverHour},
                      ${if (default.warmUpEnabled) 1 else 0},
                      ${if (default.stretchEnabled) 1 else 0},
                      ${if (default.countRoutinesInTotals) 1 else 0},
                      ${if (default.lockFutureDays) 1 else 0}, '$programIds', $now
               FROM cycles"""
        )
    }
}

/**
 * v2 -> v3: makes the exercise catalog editable. `slot` (PROGRAM/WARMUP/STRETCH) replaces
 * `category` as what decides circuit membership — `category` becomes display-only. The
 * rest are new mutability/tracking columns, all defaulted so existing rows need no
 * further backfill beyond `slot`.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercises ADD COLUMN `slot` TEXT NOT NULL DEFAULT 'PROGRAM'")
        db.execSQL("ALTER TABLE exercises ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE exercises ADD COLUMN `archivedAt` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE exercises ADD COLUMN `isCustom` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE exercises ADD COLUMN `tracksReps` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE exercises ADD COLUMN `tracksLoad` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE exercises ADD COLUMN `defaultLoadKg` REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE exercises ADD COLUMN `defaultBandLevel` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE exercises ADD COLUMN `progressionStep` INTEGER NOT NULL DEFAULT 0")

        db.execSQL("UPDATE exercises SET slot = 'WARMUP' WHERE category = 'WARMUP'")
        db.execSQL("UPDATE exercises SET slot = 'STRETCH' WHERE category = 'STRETCH'")
        db.execSQL("UPDATE exercises SET slot = 'PROGRAM' WHERE category IN ('BODYWEIGHT', 'BAND')")
    }
}

/**
 * v3 -> v4: adds optional set-detail columns to `completions` for rep/load logging.
 * All six are nullable with no backfill — NULL means "not logged", which is exactly
 * right for every row recorded before this feature existed (INVARIANT 5: every aggregate
 * stays a `COUNT(*)` regardless of whether these are set).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE completions ADD COLUMN `reps` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE completions ADD COLUMN `loadKg` REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE completions ADD COLUMN `bandLevel` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE completions ADD COLUMN `holdSeconds` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE completions ADD COLUMN `rpe` INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE completions ADD COLUMN `note` TEXT DEFAULT NULL")
    }
}

/**
 * v4 -> v5: adds `metrics` (the editable measurement catalog) and `measurements`
 * (canonical kg/cm/percent readings). `CREATE TABLE` statements copied verbatim from
 * the Room-generated schemas/5.json, same reasoning as MIGRATION_1_2.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `metrics` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `hint` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `isCustom` INTEGER NOT NULL, `decimals` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `archivedAt` INTEGER, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `metricId` TEXT NOT NULL, `value` REAL NOT NULL, `takenAt` INTEGER NOT NULL, `note` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_measurements_metricId_takenAt` ON `measurements` (`metricId`, `takenAt`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_measurements_takenAt` ON `measurements` (`takenAt`)"
        )

        SeedData.DEFAULT_METRICS.forEach { m ->
            db.execSQL(
                """INSERT OR IGNORE INTO metrics
                   (id, name, kind, hint, enabled, isCustom, decimals, sortOrder)
                   VALUES ('${m.id}', '${m.name.replace("'", "''")}', '${m.kind}',
                           '${m.hint.replace("'", "''")}', ${if (m.enabled) 1 else 0},
                           ${if (m.isCustom) 1 else 0}, ${m.decimals}, ${m.sortOrder})"""
            )
        }
    }
}
