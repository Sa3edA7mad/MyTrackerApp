package com.example.mytrackerapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mytrackerapp.data.entity.CompletionEntity
import com.example.mytrackerapp.data.entity.CycleEntity
import com.example.mytrackerapp.data.entity.DayEntity
import com.example.mytrackerapp.data.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

/* --------------------------------------------------------------- projections */

data class CircuitCount(val week: Int, val day: Int, val circuit: Int, val done: Int)

data class DayCount(val week: Int, val day: Int, val done: Int)

data class ExerciseCount(val exerciseId: String, val done: Int)

/* --------------------------------------------------------------------- DAOs */

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY sortOrder")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE category = :category ORDER BY sortOrder")
    fun observeByCategory(category: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE category = :category ORDER BY sortOrder")
    suspend fun getByCategory(category: String): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises ORDER BY sortOrder")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExerciseEntity>)
}

@Dao
interface CycleDao {

    @Query("SELECT * FROM cycles WHERE isActive = 1 ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<CycleEntity?>

    @Query("SELECT * FROM cycles WHERE isActive = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getActive(): CycleEntity?

    @Query("SELECT * FROM cycles ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles ORDER BY startedAt ASC")
    suspend fun getAll(): List<CycleEntity>

    @Query("SELECT COUNT(*) FROM cycles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(cycle: CycleEntity): Long

    @Query("UPDATE cycles SET isActive = 0, completedAt = :ts WHERE id = :id")
    suspend fun complete(id: Long, ts: Long)
}

@Dao
interface DayDao {

    @Query("SELECT * FROM days WHERE cycleId = :cycleId")
    fun observeForCycle(cycleId: Long): Flow<List<DayEntity>>

    @Query("SELECT * FROM days WHERE cycleId = :cycleId")
    suspend fun getForCycle(cycleId: Long): List<DayEntity>

    @Query("SELECT * FROM days WHERE cycleId = :cycleId AND week = :week AND day = :day")
    fun observe(cycleId: Long, week: Int, day: Int): Flow<DayEntity?>

    @Query("SELECT * FROM days WHERE cycleId = :cycleId AND week = :week AND day = :day")
    suspend fun get(cycleId: Long, week: Int, day: Int): DayEntity?

    /** Returns -1 when the row already existed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(day: DayEntity): Long

    @Query(
        "UPDATE days SET warmUpDoneAt = :ts WHERE cycleId = :cycleId AND week = :week AND day = :day"
    )
    suspend fun markWarmUp(cycleId: Long, week: Int, day: Int, ts: Long)

    @Query(
        "UPDATE days SET stretchDoneAt = :ts WHERE cycleId = :cycleId AND week = :week AND day = :day"
    )
    suspend fun markStretch(cycleId: Long, week: Int, day: Int, ts: Long)

    @Query(
        "UPDATE days SET closedAt = :ts WHERE cycleId = :cycleId AND week = :week AND day = :day"
    )
    suspend fun close(cycleId: Long, week: Int, day: Int, ts: Long)

    @Query("DELETE FROM days WHERE cycleId = :cycleId")
    suspend fun deleteForCycle(cycleId: Long)
}

/**
 * INVARIANT 2: every aggregate here filters `circuit >= 1`.
 *
 * Warm-up (circuit 0) and stretch (circuit -1) completions are stored so a half-done
 * routine resumes, but they must never enter a day, week, or cycle total. Adding a
 * query without that filter will silently inflate every number in the app.
 */
@Dao
interface CompletionDao {

    /** IGNORE + the unique index is what makes a double-tap idempotent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: CompletionEntity): Long

    @Query(
        """DELETE FROM completions
           WHERE cycleId = :cycleId AND week = :week AND day = :day
             AND circuit = :circuit AND exerciseId = :exerciseId"""
    )
    suspend fun delete(cycleId: Long, week: Int, day: Int, circuit: Int, exerciseId: String)

    @Query(
        """SELECT week, day, circuit, COUNT(*) AS done FROM completions
           WHERE cycleId = :cycleId AND circuit >= 1
           GROUP BY week, day, circuit"""
    )
    fun observeCircuitCounts(cycleId: Long): Flow<List<CircuitCount>>

    @Query(
        """SELECT week, day, COUNT(*) AS done FROM completions
           WHERE cycleId = :cycleId AND circuit >= 1
           GROUP BY week, day"""
    )
    fun observeDayCounts(cycleId: Long): Flow<List<DayCount>>

    @Query(
        """SELECT week, day, COUNT(*) AS done FROM completions
           WHERE cycleId = :cycleId AND circuit >= 1
           GROUP BY week, day"""
    )
    suspend fun getDayCounts(cycleId: Long): List<DayCount>

    @Query(
        """SELECT exerciseId FROM completions
           WHERE cycleId = :cycleId AND week = :week AND day = :day AND circuit = :circuit"""
    )
    fun observeExerciseIdsIn(
        cycleId: Long,
        week: Int,
        day: Int,
        circuit: Int
    ): Flow<List<String>>

    @Query(
        """SELECT exerciseId FROM completions
           WHERE cycleId = :cycleId AND week = :week AND day = :day AND circuit = :circuit"""
    )
    suspend fun getExerciseIdsIn(
        cycleId: Long,
        week: Int,
        day: Int,
        circuit: Int
    ): List<String>

    @Query("SELECT COUNT(*) FROM completions WHERE cycleId = :cycleId AND circuit >= 1")
    fun observeTotalDone(cycleId: Long): Flow<Int>

    /** Progress screen only — can return up to 1,716 rows. Never call this from Today. */
    @Query("SELECT completedAt FROM completions WHERE cycleId = :cycleId AND circuit >= 1")
    fun observeCompletionTimes(cycleId: Long): Flow<List<Long>>

    @Query(
        """SELECT exerciseId, COUNT(*) AS done FROM completions
           WHERE cycleId = :cycleId AND circuit >= 1
           GROUP BY exerciseId ORDER BY done DESC"""
    )
    fun observeTallies(cycleId: Long): Flow<List<ExerciseCount>>

    @Query(
        """SELECT COUNT(*) FROM completions
           WHERE cycleId = :cycleId AND exerciseId = :exerciseId AND circuit >= 1"""
    )
    fun observeCountForExercise(cycleId: Long, exerciseId: String): Flow<Int>

    @Query(
        """SELECT completedAt FROM completions
           WHERE cycleId = :cycleId AND exerciseId = :exerciseId AND circuit >= 1"""
    )
    fun observeTimesForExercise(cycleId: Long, exerciseId: String): Flow<List<Long>>

    /**
     * Deliberately NOT filtered by circuit — the only such query in this DAO.
     *
     * INVARIANT 2 governs program *totals*. This feeds the per-exercise history on the
     * detail screen, where a warm-up move should be able to show "done 12 times" rather
     * than always reading zero. Warm-up/stretch ids are disjoint from the 13 program
     * exercises, so for a program exercise this returns exactly the same rows as
     * [observeTimesForExercise]. Never use it for a day, week, or cycle total.
     */
    @Query(
        """SELECT completedAt FROM completions
           WHERE cycleId = :cycleId AND exerciseId = :exerciseId"""
    )
    fun observeAllTimesForExercise(cycleId: Long, exerciseId: String): Flow<List<Long>>

    @Query("DELETE FROM completions WHERE cycleId = :cycleId")
    suspend fun deleteForCycle(cycleId: Long)

    @Query("SELECT * FROM completions WHERE cycleId = :cycleId")
    suspend fun getAllForCycle(cycleId: Long): List<CompletionEntity>
}
