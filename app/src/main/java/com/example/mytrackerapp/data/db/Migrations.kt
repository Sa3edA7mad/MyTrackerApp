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
