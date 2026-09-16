package com.example.mytrackerapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mytrackerapp.data.entity.CompletionEntity
import com.example.mytrackerapp.data.entity.CycleEntity
import com.example.mytrackerapp.data.entity.DayEntity
import com.example.mytrackerapp.data.entity.ExerciseEntity
import com.example.mytrackerapp.data.seed.SeedData

@Database(
    entities = [
        ExerciseEntity::class,
        CycleEntity::class,
        DayEntity::class,
        CompletionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun cycleDao(): CycleDao
    abstract fun dayDao(): DayDao
    abstract fun completionDao(): CompletionDao

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
                    perSide, targetLabel, videoUrl, sortOrder)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
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
                    e.sortOrder
                )
            )
        }

        // INVARIANT 1: there is always exactly one active cycle. Without this the very
        // first launch has no cycle to read and every screen renders empty.
        db.execSQL(
            "INSERT INTO cycles (startedAt, completedAt, isActive) VALUES (?, NULL, 1)",
            arrayOf(System.currentTimeMillis())
        )
    }
}
