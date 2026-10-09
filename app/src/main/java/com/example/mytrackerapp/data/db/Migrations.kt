package com.example.mytrackerapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mytrackerapp.data.entity.ExerciseEntity
import com.example.mytrackerapp.data.seed.LibrarySeed
import com.example.mytrackerapp.data.seed.SeedData
import com.example.mytrackerapp.data.seed.StarterPrograms
import com.example.mytrackerapp.domain.PlanCodec
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

/**
 * v5 -> v6: data only. Strips the " exercise proper form" suffix from seeded YouTube
 * search links so installed catalogs match the new [SeedData], which searches for the
 * exercise name alone. The schema is unchanged.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """UPDATE exercises
               SET videoUrl = REPLACE(videoUrl, '%20exercise%20proper%20form', '')
               WHERE instr(videoUrl, 'youtube.com/results?search_query=') > 0"""
        )
    }
}

/**
 * v6 -> v7: programs.
 *
 * - `programs` table; the existing install becomes program 1, "Home 4-Week". Its rules row
 *   (`program_rules.id = 1`) and every existing cycle (`cycles.programId` defaults to 1) are
 *   already Home's, so no history moves. Home's blank `planText` means "the catalog-slot plan",
 *   exactly what it ran before; old `cycle_rules` rows keep reading `programExerciseIdsCsv`.
 * - `completions.setNumber` (every existing row is set 1) joins the unique index, so a
 *   multi-set exercise can be ticked once per set.
 * - `circuit_results` for AMRAP / for-time scores.
 * - The library import: 123 exercises in the LIBRARY slot, in no circuit, plus sheet
 *   metadata merged into Cat-Cow and Child's Pose. INSERT OR IGNORE leaves a user's own
 *   exercise alone if it already took one of the ids.
 * - Two inactive starter programs (Gym Strength, CrossFit Conditioning).
 *
 * `CREATE` statements copied verbatim from the Room-generated schemas/7.json.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("equipment", "level", "cue", "videoTitle", "videoChannel").forEach {
            db.execSQL("ALTER TABLE exercises ADD COLUMN `$it` TEXT NOT NULL DEFAULT ''")
        }
        db.execSQL("ALTER TABLE completions ADD COLUMN `setNumber` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("DROP INDEX IF EXISTS `index_completions_cycleId_week_day_circuit_exerciseId`")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_completions_cycleId_week_day_circuit_exerciseId_setNumber` ON `completions` (`cycleId`, `week`, `day`, `circuit`, `exerciseId`, `setNumber`)"
        )
        db.execSQL("ALTER TABLE cycles ADD COLUMN `programId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE program_rules ADD COLUMN `planText` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE cycle_rules ADD COLUMN `planText` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `programs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `active` INTEGER NOT NULL, `archivedAt` INTEGER, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `circuit_results` (`cycleId` INTEGER NOT NULL, `week` INTEGER NOT NULL, `day` INTEGER NOT NULL, `circuit` INTEGER NOT NULL, `value` INTEGER NOT NULL, `recordedAt` INTEGER NOT NULL, PRIMARY KEY(`cycleId`, `week`, `day`, `circuit`), FOREIGN KEY(`cycleId`) REFERENCES `cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )

        // Merge the workbook's metadata into the two exercises it shares with Home. The real
        // video only replaces a link the user never changed (the seeded search, or blank).
        SeedData.ALL_EXERCISES.filter { it.equipment.isNotEmpty() }.forEach { e ->
            db.execSQL(
                """UPDATE exercises SET equipment = ?, level = ?, cue = ?, videoTitle = ?,
                   videoChannel = ? WHERE id = ?""",
                arrayOf(e.equipment, e.level, e.cue, e.videoTitle, e.videoChannel, e.id)
            )
            db.execSQL(
                """UPDATE exercises SET videoUrl = ?
                   WHERE id = ? AND (videoUrl = '' OR instr(videoUrl, 'youtube.com/results?') > 0)""",
                arrayOf(e.videoUrl, e.id)
            )
        }
        LibrarySeed.EXERCISES.forEach { insertExercise(db, it, orIgnore = true) }

        val units = db.query("SELECT weightUnit, lengthUnit FROM program_rules WHERE id = 1").use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getString(1) else "KG" to "CM"
        }
        insertPrograms(db, units.first, units.second, System.currentTimeMillis())
    }
}

/** One catalog row, every column. Shared by [SeedCallback] and [MIGRATION_6_7] (INVARIANT 9). */
internal fun insertExercise(db: SupportSQLiteDatabase, e: ExerciseEntity, orIgnore: Boolean) {
    db.execSQL(
        """INSERT ${if (orIgnore) "OR IGNORE " else ""}INTO exercises
           (id, name, category, muscles, instructions, targetType, targetValue,
            perSide, targetLabel, videoUrl, sortOrder, slot, enabled, archivedAt,
            isCustom, tracksReps, tracksLoad, defaultLoadKg, defaultBandLevel,
            progressionStep, equipment, level, cue, videoTitle, videoChannel)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?, ?)""",
        arrayOf<Any>(
            e.id, e.name, e.category, e.muscles, e.instructions, e.targetType, e.targetValue,
            if (e.perSide) 1 else 0, e.targetLabel, e.videoUrl, e.sortOrder, e.slot,
            if (e.enabled) 1 else 0, if (e.isCustom) 1 else 0, if (e.tracksReps) 1 else 0,
            if (e.tracksLoad) 1 else 0, e.progressionStep, e.equipment, e.level, e.cue,
            e.videoTitle, e.videoChannel
        )
    )
}

/**
 * The Home program row plus the starter programs and their rules rows. Program 1's rules
 * row already exists (seeded or migrated); the starters copy its display units.
 */
internal fun insertPrograms(db: SupportSQLiteDatabase, weightUnit: String, lengthUnit: String, now: Long) {
    db.execSQL(
        "INSERT OR IGNORE INTO programs (id, name, active, archivedAt, createdAt) VALUES (?, ?, 1, NULL, ?)",
        arrayOf<Any>(StarterPrograms.HOME_ID, StarterPrograms.HOME_NAME, now)
    )
    StarterPrograms.ALL.forEach { p ->
        db.execSQL(
            "INSERT OR IGNORE INTO programs (id, name, active, archivedAt, createdAt) VALUES (?, ?, 0, NULL, ?)",
            arrayOf<Any>(p.id, p.name, now)
        )
        val r = p.rules
        db.execSQL(
            """INSERT OR IGNORE INTO program_rules
               (id, weeks, daysPerWeek, circuitsPerWeekCsv, dayRolloverHour, warmUpEnabled,
                stretchEnabled, countRoutinesInTotals, lockFutureDays, weightUnit, lengthUnit,
                updatedAt, planText)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf<Any>(
                p.id, r.weeks, r.daysPerWeek, ProgramRules.circuitsCsv(r.circuitsPerWeek),
                r.dayRolloverHour, if (r.warmUpEnabled) 1 else 0, if (r.stretchEnabled) 1 else 0,
                if (r.countRoutinesInTotals) 1 else 0, if (r.lockFutureDays) 1 else 0,
                weightUnit, lengthUnit, now, PlanCodec.encode(p.plan)
            )
        )
    }
}
