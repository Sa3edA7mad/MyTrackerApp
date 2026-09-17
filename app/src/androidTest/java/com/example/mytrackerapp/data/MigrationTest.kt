package com.example.mytrackerapp.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.MIGRATION_1_2
import com.example.mytrackerapp.data.db.MIGRATION_2_3
import com.example.mytrackerapp.data.db.MIGRATION_3_4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises every Migration against a real on-disk database created at the old schema
 * version, the way an upgrading install actually experiences it — MigrationTestHelper
 * validates the resulting schema against the exported JSON, catching the "hand-written SQL
 * doesn't quite match what Room generated" failure mode named in the plan's risk table.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2() {
        val dbName = "migration-test-1-2"

        // Build a v1 database with a cycle and a couple of completions.
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO cycles (id, startedAt, completedAt, isActive) VALUES (1, 1000, NULL, 1)"
            )
            execSQL(
                """INSERT INTO completions (cycleId, week, day, circuit, exerciseId, completedAt)
                   VALUES (1, 1, 1, 1, 'squat', 2000)"""
            )
            execSQL(
                """INSERT INTO completions (cycleId, week, day, circuit, exerciseId, completedAt)
                   VALUES (1, 1, 1, 1, 'push_up', 2100)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        // The pre-existing completions survived untouched.
        migrated.query("SELECT COUNT(*) FROM completions WHERE cycleId = 1").use { c ->
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
        }

        // Exactly one program_rules row, with the shipped default shape.
        migrated.query("SELECT circuitsPerWeekCsv, weeks, daysPerWeek FROM program_rules").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("4,5,6,7", c.getString(0))
            assertEquals(4, c.getInt(1))
            assertEquals(6, c.getInt(2))
        }

        // One cycle_rules row per existing cycle.
        migrated.query("SELECT COUNT(*) FROM cycle_rules WHERE cycleId = 1").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        migrated.query("SELECT exercisesPerCircuit FROM cycle_rules WHERE cycleId = 1").use { c ->
            c.moveToFirst()
            assertEquals(13, c.getInt(0))
        }
    }

    @Test
    fun migrate2To3() {
        val dbName = "migration-test-2-3"

        helper.createDatabase(dbName, 2).apply {
            execSQL(
                """INSERT INTO exercises
                   (id, name, category, muscles, instructions, targetType, targetValue,
                    perSide, targetLabel, videoUrl, sortOrder)
                   VALUES ('neck_rolls', 'Neck Rolls', 'WARMUP', '', 'Roll your neck.',
                           'REPS', 8, 0, '8 reps', '', 101)"""
            )
            execSQL(
                """INSERT INTO exercises
                   (id, name, category, muscles, instructions, targetType, targetValue,
                    perSide, targetLabel, videoUrl, sortOrder)
                   VALUES ('squat', 'Squat', 'BODYWEIGHT', 'Legs', 'Squat down.',
                           'REPS', 5, 0, '5 reps', '', 1)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        migrated.query("SELECT slot, enabled FROM exercises WHERE id = 'neck_rolls'").use { c ->
            c.moveToFirst()
            assertEquals("WARMUP", c.getString(0))
            assertEquals(1, c.getInt(1))
        }
        migrated.query("SELECT slot FROM exercises WHERE id = 'squat'").use { c ->
            c.moveToFirst()
            assertEquals("PROGRAM", c.getString(0))
        }
    }

    @Test
    fun migrate3To4() {
        val dbName = "migration-test-3-4"

        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO cycles (id, startedAt, completedAt, isActive) VALUES (1, 1000, NULL, 1)"
            )
            execSQL(
                """INSERT INTO completions (cycleId, week, day, circuit, exerciseId, completedAt)
                   VALUES (1, 1, 1, 1, 'squat', 2000)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        // Existing rows survive with NULL detail — presence, not detail, is the truth.
        migrated.query(
            "SELECT reps, loadKg, bandLevel, holdSeconds, rpe, note FROM completions WHERE exerciseId = 'squat'"
        ).use { c ->
            c.moveToFirst()
            for (col in 0..5) assertNull(c.getString(col))
        }

        // The double-tap-idempotency index must have survived the ALTER TABLE.
        migrated.query(
            """SELECT COUNT(*) FROM sqlite_master
               WHERE type = 'index' AND name = 'index_completions_cycleId_week_day_circuit_exerciseId'"""
        ).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }
}
