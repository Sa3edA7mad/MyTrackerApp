package com.example.mytrackerapp.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.MIGRATION_1_2
import com.example.mytrackerapp.data.db.MIGRATION_2_3
import com.example.mytrackerapp.data.db.MIGRATION_3_4
import com.example.mytrackerapp.data.db.MIGRATION_4_5
import com.example.mytrackerapp.data.db.MIGRATION_5_6
import com.example.mytrackerapp.data.db.MIGRATION_6_7
import com.example.mytrackerapp.data.db.SeedCallback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    @Test
    fun migrate4To5() {
        val dbName = "migration-test-4-5"
        helper.createDatabase(dbName, 4).close()

        val migrated = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        migrated.query("SELECT COUNT(*) FROM metrics").use { c ->
            c.moveToFirst()
            assertEquals(17, c.getInt(0))
        }
        migrated.query("SELECT enabled FROM metrics WHERE id = 'bodyweight'").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        migrated.query("SELECT enabled FROM metrics WHERE id = 'neck'").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun migrate5To6() {
        val dbName = "migration-test-5-6"
        helper.createDatabase(dbName, 5).apply {
            fun insert(id: String, url: String) = execSQL(
                """INSERT INTO exercises
                   (id, name, category, muscles, instructions, targetType, targetValue, perSide,
                    targetLabel, videoUrl, sortOrder, slot, enabled, archivedAt, isCustom,
                    tracksReps, tracksLoad, defaultLoadKg, defaultBandLevel, progressionStep)
                   VALUES ('$id', '$id', 'BODYWEIGHT', '', '', 'REPS', 5, 0, '5 reps', '$url',
                           1, 'PROGRAM', 1, NULL, 0, 0, 0, NULL, NULL, 0)"""
            )
            insert(
                "dead_hang",
                "https://www.youtube.com/results?search_query=Dead%20Hang%20exercise%20proper%20form"
            )
            insert("push_up", "https://www.youtube.com/watch?v=WDIpL0pjun0")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        fun url(id: String) = migrated.query("SELECT videoUrl FROM exercises WHERE id = '$id'")
            .use { c -> c.moveToFirst(); c.getString(0) }
        assertEquals("https://www.youtube.com/results?search_query=Dead%20Hang", url("dead_hang"))
        assertEquals("https://www.youtube.com/watch?v=WDIpL0pjun0", url("push_up"))
    }

    /**
     * v6 -> v7: the install becomes the Home program without moving any history, every old
     * completion becomes set 1, the library lands in the LIBRARY slot, and a user's own
     * exercise that already took a library id is left alone.
     */
    @Test
    fun migrate6To7() {
        val dbName = "migration-test-6-7"
        helper.createDatabase(dbName, 6).apply {
            fun insert(id: String, slot: String, url: String, custom: Int = 0) = execSQL(
                """INSERT INTO exercises
                   (id, name, category, muscles, instructions, targetType, targetValue, perSide,
                    targetLabel, videoUrl, sortOrder, slot, enabled, archivedAt, isCustom,
                    tracksReps, tracksLoad, defaultLoadKg, defaultBandLevel, progressionStep)
                   VALUES ('$id', 'My $id', 'BODYWEIGHT', '', '', 'REPS', 5, 0, '5 reps', '$url',
                           1, '$slot', 1, NULL, $custom, 0, 0, NULL, NULL, 0)"""
            )
            insert("cat_cow", "WARMUP", "https://www.youtube.com/results?search_query=Cat-Cow")
            insert("childs_pose", "STRETCH", "https://example.com/my-own-video")
            insert("plank", "PROGRAM", "", custom = 1)
            execSQL(
                """INSERT INTO program_rules (id, weeks, daysPerWeek, circuitsPerWeekCsv, dayRolloverHour,
                   warmUpEnabled, stretchEnabled, countRoutinesInTotals, lockFutureDays, weightUnit,
                   lengthUnit, updatedAt) VALUES (1, 4, 6, '4,5,6,7', 4, 1, 1, 0, 1, 'LB', 'IN', 1)"""
            )
            execSQL("INSERT INTO cycles (id, startedAt, completedAt, isActive) VALUES (1, 1000, NULL, 1)")
            execSQL(
                """INSERT INTO completions (cycleId, week, day, circuit, exerciseId, completedAt)
                   VALUES (1, 1, 1, 1, 'plank', 2000)"""
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        fun string(sql: String) = migrated.query(sql).use { c -> c.moveToFirst(); c.getString(0) }
        fun int(sql: String) = migrated.query(sql).use { c -> c.moveToFirst(); c.getInt(0) }

        assertEquals("Home 4-Week|1", string("SELECT name || '|' || active FROM programs WHERE id = 1"))
        assertEquals(3, int("SELECT COUNT(*) FROM programs"))
        assertEquals(0, int("SELECT COUNT(*) FROM programs WHERE id > 1 AND active = 1"))
        assertEquals(1, int("SELECT programId FROM cycles WHERE id = 1"))
        assertEquals(1, int("SELECT setNumber FROM completions WHERE exerciseId = 'plank'"))
        assertEquals("", string("SELECT planText FROM program_rules WHERE id = 1"))
        assertEquals("LB", string("SELECT weightUnit FROM program_rules WHERE id = 2"))
        assertEquals(1, int("SELECT COUNT(*) FROM program_rules WHERE id = 3 AND planText LIKE '%kettlebell_swing%'"))

        // 123 library rows; the user's own "plank" kept its slot and name.
        assertEquals(3 + 122, int("SELECT COUNT(*) FROM exercises"))
        assertEquals("My plank|PROGRAM", string("SELECT name || '|' || slot FROM exercises WHERE id = 'plank'"))
        assertEquals("LIBRARY", string("SELECT slot FROM exercises WHERE id = 'barbell_back_squat'"))

        // Merged metadata; the real video replaces only the untouched seeded search.
        assertEquals("Mat", string("SELECT equipment FROM exercises WHERE id = 'cat_cow'"))
        assertEquals("https://www.youtube.com/watch?v=96sQ-N5VBnA", string("SELECT videoUrl FROM exercises WHERE id = 'cat_cow'"))
        assertEquals("https://example.com/my-own-video", string("SELECT videoUrl FROM exercises WHERE id = 'childs_pose'"))
    }

    /**
     * INVARIANT 9: a fresh install ([SeedCallback]) and a migrated-from-v1 install
     * (every Migration) must agree on program_rules and metrics content. This is what
     * catches the seed-lives-in-two-places drift the plan calls out as risk #2.
     */
    @Test
    fun freshInstallMatchesMigrated() = runBlocking {
        val fresh = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).addCallback(SeedCallback).build()
        val freshRules = fresh.rulesDao().getRules()!!
        val freshGymPlan = fresh.rulesDao().getRules(2)!!.planText
        val freshPrograms = fresh.programDao().observeAll().first().map { it.name to it.active }
        fresh.close()

        val dbName = "migration-test-fresh-vs-migrated"
        helper.createDatabase(dbName, 1).close()
        val migrated = helper.runMigrationsAndValidate(
            dbName, 7, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
        )

        migrated.query("SELECT name, active FROM programs ORDER BY id").use { c ->
            val rows = generateSequence { if (c.moveToNext()) c.getString(0) to (c.getInt(1) == 1) else null }.toList()
            assertEquals(freshPrograms, rows)
        }
        migrated.query("SELECT planText FROM program_rules WHERE id = 2").use { c ->
            c.moveToFirst()
            assertEquals(freshGymPlan, c.getString(0))
        }

        migrated.query(
            "SELECT weeks, daysPerWeek, circuitsPerWeekCsv FROM program_rules WHERE id = 1"
        ).use { c ->
            c.moveToFirst()
            assertEquals(freshRules.weeks, c.getInt(0))
            assertEquals(freshRules.daysPerWeek, c.getInt(1))
            assertEquals(freshRules.circuitsPerWeekCsv, c.getString(2))
        }

        migrated.query("SELECT COUNT(*) FROM metrics").use { c ->
            c.moveToFirst()
            assertEquals(17, c.getInt(0))
        }
        migrated.query("SELECT enabled FROM metrics WHERE id = 'bodyweight'").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }
}
