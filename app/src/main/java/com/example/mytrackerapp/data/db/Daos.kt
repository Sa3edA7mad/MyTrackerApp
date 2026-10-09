package com.example.mytrackerapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
import kotlinx.coroutines.flow.Flow

/* --------------------------------------------------------------- projections */

data class CircuitCount(val week: Int, val day: Int, val circuit: Int, val done: Int)

data class DayCount(val week: Int, val day: Int, val done: Int)

data class ExerciseCount(val exerciseId: String, val done: Int)

data class DoneKey(val exerciseId: String, val setNumber: Int)

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

    /** Not archived — what the Library and any circuit should ever show. */
    @Query("SELECT * FROM exercises WHERE archivedAt IS NULL ORDER BY sortOrder")
    fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE archivedAt IS NULL ORDER BY sortOrder")
    suspend fun getActive(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE slot = :slot AND archivedAt IS NULL ORDER BY sortOrder")
    fun observeBySlot(slot: String): Flow<List<ExerciseEntity>>

    /** Enabled, not archived — the count that decides `exercisesPerCircuit`/warm-up/stretch counts. */
    @Query(
        "SELECT COUNT(*) FROM exercises WHERE slot = :slot AND enabled = 1 AND archivedAt IS NULL"
    )
    suspend fun countBySlot(slot: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exercise: ExerciseEntity)

    @Query("UPDATE exercises SET archivedAt = :ts WHERE id = :id")
    suspend fun archive(id: String, ts: Long)

    @Query("UPDATE exercises SET archivedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("UPDATE exercises SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE exercises SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int)
}

@Dao
interface CycleDao {

    @Query("SELECT * FROM cycles WHERE isActive = 1 AND programId = :programId ORDER BY id DESC LIMIT 1")
    fun observeActive(programId: Long = 1): Flow<CycleEntity?>

    @Query("SELECT * FROM cycles WHERE isActive = 1 AND programId = :programId ORDER BY id DESC LIMIT 1")
    suspend fun getActive(programId: Long = 1): CycleEntity?

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
interface ProgramDao {

    @Query("SELECT * FROM programs ORDER BY id")
    fun observeAll(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs ORDER BY id")
    suspend fun getAll(): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE active = 1 AND archivedAt IS NULL ORDER BY id")
    fun observeActive(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs WHERE active = 1 AND archivedAt IS NULL ORDER BY id")
    suspend fun getActive(): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE id = :id")
    fun observe(id: Long): Flow<ProgramEntity?>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getById(id: Long): ProgramEntity?

    @Insert
    suspend fun insert(program: ProgramEntity): Long

    @Query("UPDATE programs SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE programs SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    /** Archiving also pauses: an archived program never shows on Today. */
    @Query("UPDATE programs SET archivedAt = :ts, active = 0 WHERE id = :id")
    suspend fun archive(id: Long, ts: Long?)
}

@Dao
interface CircuitResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: CircuitResultEntity)

    @Query(
        """SELECT * FROM circuit_results
           WHERE cycleId = :cycleId AND week = :week AND day = :day AND circuit = :circuit"""
    )
    fun observe(cycleId: Long, week: Int, day: Int, circuit: Int): Flow<CircuitResultEntity?>

    @Query("SELECT * FROM circuit_results WHERE cycleId = :cycleId")
    suspend fun getAllForCycle(cycleId: Long): List<CircuitResultEntity>

    @Query("DELETE FROM circuit_results WHERE cycleId = :cycleId")
    suspend fun deleteForCycle(cycleId: Long)
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
 * INVARIANT 2: every aggregate here filters `circuit >= 1` unless [includeRoutines] is set.
 *
 * Warm-up (circuit 0) and stretch (circuit -1) completions are stored so a half-done
 * routine resumes. Whether they enter a day/week/cycle total is now a rule
 * (`ProgramRules.countRoutinesInTotals`), not a law — every aggregate query below still
 * takes an explicit flag so nothing can silently forget to check it.
 */
@Dao
interface CompletionDao {

    /** IGNORE + the unique index is what makes a double-tap idempotent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: CompletionEntity): Long

    @Query(
        """DELETE FROM completions
           WHERE cycleId = :cycleId AND week = :week AND day = :day
             AND circuit = :circuit AND exerciseId = :exerciseId AND setNumber = :setNumber"""
    )
    suspend fun delete(
        cycleId: Long,
        week: Int,
        day: Int,
        circuit: Int,
        exerciseId: String,
        setNumber: Int = 1
    )

    @Query(
        """SELECT week, day, circuit, COUNT(*) AS done FROM completions
           WHERE cycleId = :cycleId AND (:includeRoutines OR circuit >= 1)
           GROUP BY week, day, circuit"""
    )
    fun observeCircuitCounts(cycleId: Long, includeRoutines: Boolean = false): Flow<List<CircuitCount>>

    @Query(
        """SELECT week, day, COUNT(*) AS done FROM completions
           WHERE cycleId = :cycleId AND (:includeRoutines OR circuit >= 1)
           GROUP BY week, day"""
    )
    fun observeDayCounts(cycleId: Long, includeRoutines: Boolean = false): Flow<List<DayCount>>

    @Query(
        """SELECT week, day, COUNT(*) AS done FROM completions
           WHERE cycleId = :cycleId AND (:includeRoutines OR circuit >= 1)
           GROUP BY week, day"""
    )
    suspend fun getDayCounts(cycleId: Long, includeRoutines: Boolean = false): List<DayCount>

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

    /** Every ticked (exercise, set) in one circuit slot — what a circuit's done-state reads. */
    @Query(
        """SELECT exerciseId, setNumber FROM completions
           WHERE cycleId = :cycleId AND week = :week AND day = :day AND circuit = :circuit"""
    )
    fun observeDoneIn(cycleId: Long, week: Int, day: Int, circuit: Int): Flow<List<DoneKey>>

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

    @Query(
        "SELECT COUNT(*) FROM completions WHERE cycleId = :cycleId AND (:includeRoutines OR circuit >= 1)"
    )
    fun observeTotalDone(cycleId: Long, includeRoutines: Boolean = false): Flow<Int>

    /** Progress screen only — can return up to a full cycle's worth of rows. Never call this from Today. */
    @Query(
        """SELECT completedAt FROM completions
           WHERE cycleId = :cycleId AND (:includeRoutines OR circuit >= 1)"""
    )
    fun observeCompletionTimes(cycleId: Long, includeRoutines: Boolean = false): Flow<List<Long>>

    /**
     * Ties are broken by exerciseId rather than left to SQLite. Early in a cycle every
     * exercise sits on the same count, and without a deterministic tiebreak the "most
     * done" row reshuffles between reads for no visible reason.
     *
     * Deliberately does NOT join `exercises` to sort by program order. There is no foreign
     * key on `completions.exerciseId`, so a join silently drops any completion whose
     * catalog row is missing — DaoTest caught exactly that. Alphabetical is less pretty
     * than program order and strictly safer.
     */
    @Query(
        """SELECT exerciseId, COUNT(*) AS done FROM completions
           WHERE cycleId = :cycleId AND (:includeRoutines OR circuit >= 1)
           GROUP BY exerciseId
           ORDER BY done DESC, exerciseId ASC"""
    )
    fun observeTallies(cycleId: Long, includeRoutines: Boolean = false): Flow<List<ExerciseCount>>

    @Query(
        """SELECT COUNT(*) FROM completions
           WHERE cycleId = :cycleId AND exerciseId = :exerciseId AND (:includeRoutines OR circuit >= 1)"""
    )
    fun observeCountForExercise(
        cycleId: Long,
        exerciseId: String,
        includeRoutines: Boolean = false
    ): Flow<Int>

    @Query(
        """SELECT completedAt FROM completions
           WHERE cycleId = :cycleId AND exerciseId = :exerciseId AND (:includeRoutines OR circuit >= 1)"""
    )
    fun observeTimesForExercise(
        cycleId: Long,
        exerciseId: String,
        includeRoutines: Boolean = false
    ): Flow<List<Long>>

    /**
     * Deliberately NOT filtered by circuit — the only such query in this DAO.
     *
     * INVARIANT 2 governs program *totals*. This feeds the per-exercise history on the
     * detail screen, where a warm-up move should be able to show "done 12 times" rather
     * than always reading zero. Warm-up/stretch ids are disjoint from the program
     * exercises, so for a program exercise this returns exactly the same rows as
     * [observeTimesForExercise]. Never use it for a day, week, or cycle total.
     */
    @Query(
        """SELECT completedAt FROM completions
           WHERE cycleId = :cycleId AND exerciseId = :exerciseId"""
    )
    fun observeAllTimesForExercise(cycleId: Long, exerciseId: String): Flow<List<Long>>

    /**
     * [observeAllTimesForExercise] across every program's running cycle — an exercise can
     * sit in several programs, and its history is the exercise's, not one program's.
     */
    @Query(
        """SELECT completedAt FROM completions
           WHERE exerciseId = :exerciseId
             AND cycleId IN (SELECT id FROM cycles WHERE isActive = 1)"""
    )
    fun observeActiveTimesForExercise(exerciseId: String): Flow<List<Long>>

    /** Program-circuit completion times in every running cycle — the combined streak. */
    @Query(
        """SELECT completedAt FROM completions
           WHERE circuit >= 1 AND cycleId IN (SELECT id FROM cycles WHERE isActive = 1)"""
    )
    fun observeActiveTrainingTimes(): Flow<List<Long>>

    @Query("DELETE FROM completions WHERE cycleId = :cycleId")
    suspend fun deleteForCycle(cycleId: Long)

    @Query("SELECT * FROM completions WHERE cycleId = :cycleId")
    suspend fun getAllForCycle(cycleId: Long): List<CompletionEntity>

    @Query(
        """UPDATE completions SET reps = :reps, loadKg = :loadKg, bandLevel = :bandLevel,
           holdSeconds = :holdSeconds, rpe = :rpe, note = :note
           WHERE cycleId = :cycleId AND week = :week AND day = :day
             AND circuit = :circuit AND exerciseId = :exerciseId AND setNumber = :setNumber"""
    )
    suspend fun updateDetail(
        cycleId: Long,
        week: Int,
        day: Int,
        circuit: Int,
        exerciseId: String,
        reps: Int?,
        loadKg: Double?,
        bandLevel: String?,
        holdSeconds: Int?,
        rpe: Int?,
        note: String?,
        setNumber: Int = 1
    )

    /** Most recent logged detail for an exercise — used to pre-fill the next set's sheet. */
    @Query(
        """SELECT * FROM completions
           WHERE cycleId = :cycleId AND exerciseId = :exerciseId
             AND (reps IS NOT NULL OR loadKg IS NOT NULL OR holdSeconds IS NOT NULL)
           ORDER BY completedAt DESC LIMIT 1"""
    )
    suspend fun getLastDetail(cycleId: Long, exerciseId: String): CompletionEntity?

    /** [getLastDetail] across every running cycle, so a load logged in one program pre-fills another. */
    @Query(
        """SELECT * FROM completions
           WHERE exerciseId = :exerciseId
             AND cycleId IN (SELECT id FROM cycles WHERE isActive = 1)
             AND (reps IS NOT NULL OR loadKg IS NOT NULL OR holdSeconds IS NOT NULL)
           ORDER BY completedAt DESC LIMIT 1"""
    )
    suspend fun getLastActiveDetail(exerciseId: String): CompletionEntity?

    /** Every set of this exercise this cycle, logged or not — feeds [summarise]. */
    @Query(
        """SELECT * FROM completions
           WHERE cycleId = :cycleId AND exerciseId = :exerciseId
           ORDER BY completedAt ASC"""
    )
    fun observeSetLogs(cycleId: Long, exerciseId: String): Flow<List<CompletionEntity>>

    /** [observeSetLogs] across every running cycle. */
    @Query(
        """SELECT * FROM completions
           WHERE exerciseId = :exerciseId
             AND cycleId IN (SELECT id FROM cycles WHERE isActive = 1)
           ORDER BY completedAt ASC"""
    )
    fun observeActiveSetLogs(exerciseId: String): Flow<List<CompletionEntity>>
}

@Dao
interface RulesDao {

    @Query("SELECT * FROM program_rules WHERE id = :programId")
    fun observeRules(programId: Long = 1): Flow<ProgramRulesEntity?>

    @Query("SELECT * FROM program_rules WHERE id = :programId")
    suspend fun getRules(programId: Long = 1): ProgramRulesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rules: ProgramRulesEntity)

    /** Units are app-wide; every program's row carries the same pair. */
    @Query("UPDATE program_rules SET weightUnit = :weightUnit, lengthUnit = :lengthUnit")
    suspend fun setUnits(weightUnit: String, lengthUnit: String)

    @Query("UPDATE program_rules SET planText = :planText, updatedAt = :ts WHERE id = :programId")
    suspend fun setPlan(programId: Long, planText: String, ts: Long)

    @Query("SELECT * FROM cycle_rules WHERE cycleId = :cycleId")
    suspend fun getCycleRules(cycleId: Long): CycleRulesEntity?

    @Query("SELECT * FROM cycle_rules WHERE cycleId = :cycleId")
    fun observeCycleRules(cycleId: Long): Flow<CycleRulesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCycleRules(rules: CycleRulesEntity)
}

@Dao
interface MetricDao {

    @Query("SELECT * FROM metrics WHERE archivedAt IS NULL ORDER BY sortOrder")
    fun observeAll(): Flow<List<MetricEntity>>

    /** Archived rows too — the metric catalog lists them, and their history stays readable. */
    @Query("SELECT * FROM metrics ORDER BY sortOrder")
    fun observeIncludingArchived(): Flow<List<MetricEntity>>

    @Query("SELECT * FROM metrics WHERE archivedAt IS NULL AND enabled = 1 ORDER BY sortOrder")
    fun observeEnabled(): Flow<List<MetricEntity>>

    @Query("SELECT * FROM metrics WHERE id = :id")
    suspend fun getById(id: String): MetricEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MetricEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metric: MetricEntity)

    @Query("UPDATE metrics SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE metrics SET archivedAt = :ts WHERE id = :id")
    suspend fun archive(id: String, ts: Long)
}

@Dao
interface MeasurementDao {

    @Query("SELECT * FROM measurements WHERE metricId = :metricId ORDER BY takenAt ASC")
    fun observeHistory(metricId: String): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE metricId = :metricId ORDER BY takenAt DESC LIMIT 1")
    fun observeLatest(metricId: String): Flow<MeasurementEntity?>

    @Query("SELECT * FROM measurements WHERE metricId = :metricId ORDER BY takenAt ASC")
    suspend fun getHistory(metricId: String): List<MeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: MeasurementEntity): Long

    @Query("UPDATE measurements SET value = :value, note = :note WHERE id = :id")
    suspend fun update(id: Long, value: Double, note: String)

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun delete(id: Long)
}
