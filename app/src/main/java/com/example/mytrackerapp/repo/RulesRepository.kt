package com.example.mytrackerapp.repo

import com.example.mytrackerapp.data.db.CompletionDao
import com.example.mytrackerapp.data.db.DayDao
import com.example.mytrackerapp.data.db.ExerciseDao
import com.example.mytrackerapp.data.db.RulesDao
import com.example.mytrackerapp.data.entity.CycleRulesEntity
import com.example.mytrackerapp.data.entity.ProgramRulesEntity
import com.example.mytrackerapp.domain.ApplyImpact
import com.example.mytrackerapp.domain.Position
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.RuleImpact
import com.example.mytrackerapp.domain.RuleValidation
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.WeightUnit
import com.example.mytrackerapp.domain.LengthUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

fun ProgramRulesEntity.toDomain(): ProgramRules = ProgramRules(
    weeks = weeks,
    daysPerWeek = daysPerWeek,
    circuitsPerWeek = ProgramRules.parseCircuitsCsv(circuitsPerWeekCsv),
    dayRolloverHour = dayRolloverHour,
    warmUpEnabled = warmUpEnabled,
    stretchEnabled = stretchEnabled,
    countRoutinesInTotals = countRoutinesInTotals,
    lockFutureDays = lockFutureDays
    // exercisesPerCircuit/warmUpCount/stretchCount are left at their defaults here; the
    // draft's real values are filled in by RulesRepository from the live catalog (T13).
)

fun ProgramRulesEntity.toUnitPrefs(): UnitPrefs = UnitPrefs(
    weight = runCatching { WeightUnit.valueOf(weightUnit) }.getOrDefault(WeightUnit.KG),
    length = runCatching { LengthUnit.valueOf(lengthUnit) }.getOrDefault(LengthUnit.CM)
)

fun CycleRulesEntity.toDomain(): ProgramRules = ProgramRules(
    weeks = weeks,
    daysPerWeek = daysPerWeek,
    circuitsPerWeek = ProgramRules.parseCircuitsCsv(circuitsPerWeekCsv),
    exercisesPerCircuit = exercisesPerCircuit,
    warmUpCount = warmUpCount,
    stretchCount = stretchCount,
    dayRolloverHour = dayRolloverHour,
    warmUpEnabled = warmUpEnabled,
    stretchEnabled = stretchEnabled,
    countRoutinesInTotals = countRoutinesInTotals,
    lockFutureDays = lockFutureDays
)

/**
 * Rule read/write, snapshot, and apply-to-cycle. The only writer of `program_rules` and
 * `cycle_rules`.
 */
class RulesRepository(
    private val dao: RulesDao,
    private val exercises: ExerciseDao,
    private val days: DayDao,
    private val completions: CompletionDao
) {

    /** The editable draft, with the derived catalog counts (T13) filled in. */
    fun observeDraft(): Flow<ProgramRules> = dao.observeRules().map { entity ->
        (entity ?: defaultEntity()).toDomain().withDerivedCounts()
    }

    suspend fun getDraft(): ProgramRules = withContext(Dispatchers.IO) {
        (dao.getRules() ?: defaultEntity()).toDomain().withDerivedCounts()
    }

    fun observeUnits(): Flow<UnitPrefs> =
        dao.observeRules().map { (it ?: defaultEntity()).toUnitPrefs() }

    /** INVARIANT 7. Falls back to [ProgramRules.DEFAULT] when a cycle predates snapshots. */
    suspend fun rulesFor(cycleId: Long): ProgramRules = withContext(Dispatchers.IO) {
        dao.getCycleRules(cycleId)?.toDomain() ?: ProgramRules.DEFAULT
    }

    fun observeRulesFor(cycleId: Long): Flow<ProgramRules> =
        dao.observeCycleRules(cycleId).map { it?.toDomain() ?: ProgramRules.DEFAULT }

    /**
     * The program exercise ids in the order frozen when [cycleId] was snapshotted
     * (INVARIANT 7) — archiving or reordering an exercise mid-cycle cannot retroactively
     * change what a running circuit shows. Empty when the cycle predates snapshots.
     */
    fun observeProgramOrder(cycleId: Long): Flow<List<String>> =
        dao.observeCycleRules(cycleId).map { entity ->
            entity?.programExerciseIdsCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        }

    /** Fills exercisesPerCircuit/warmUpCount/stretchCount from the live, enabled catalog. */
    private suspend fun ProgramRules.withDerivedCounts(): ProgramRules {
        val program = exercises.countBySlot("PROGRAM")
        val warmUp = exercises.countBySlot("WARMUP")
        val stretch = exercises.countBySlot("STRETCH")
        return copy(
            exercisesPerCircuit = if (program > 0) program else exercisesPerCircuit,
            warmUpCount = if (warmUp > 0) warmUp else warmUpCount,
            stretchCount = if (stretch > 0) stretch else stretchCount
        )
    }

    private fun defaultEntity(): ProgramRulesEntity {
        val d = ProgramRules.DEFAULT
        return ProgramRulesEntity(
            id = 1,
            weeks = d.weeks,
            daysPerWeek = d.daysPerWeek,
            circuitsPerWeekCsv = ProgramRules.circuitsCsv(d.circuitsPerWeek),
            dayRolloverHour = d.dayRolloverHour,
            warmUpEnabled = d.warmUpEnabled,
            stretchEnabled = d.stretchEnabled,
            countRoutinesInTotals = d.countRoutinesInTotals,
            lockFutureDays = d.lockFutureDays,
            weightUnit = WeightUnit.KG.name,
            lengthUnit = LengthUnit.CM.name,
            updatedAt = System.currentTimeMillis()
        )
    }

    /** Validates, then saves the draft. Returns the validation errors; empty means saved. */
    suspend fun saveDraft(rules: ProgramRules, units: UnitPrefs): List<String> =
        withContext(Dispatchers.IO) {
            val errors = RuleValidation.validate(rules)
            if (errors.isNotEmpty()) return@withContext errors

            dao.upsert(
                ProgramRulesEntity(
                    id = 1,
                    weeks = rules.weeks,
                    daysPerWeek = rules.daysPerWeek,
                    circuitsPerWeekCsv = ProgramRules.circuitsCsv(rules.circuitsPerWeek),
                    dayRolloverHour = rules.dayRolloverHour,
                    warmUpEnabled = rules.warmUpEnabled,
                    stretchEnabled = rules.stretchEnabled,
                    countRoutinesInTotals = rules.countRoutinesInTotals,
                    lockFutureDays = rules.lockFutureDays,
                    weightUnit = units.weight.name,
                    lengthUnit = units.length.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
            emptyList()
        }

    /**
     * What applying [candidate] (the saved draft, if omitted) to [cycleId] would do.
     * Pure analysis, writes nothing. Taking an explicit candidate lets the editor screen
     * show a live impact preview against in-progress edits the user hasn't saved yet.
     */
    suspend fun previewApply(cycleId: Long, candidate: ProgramRules? = null): ApplyImpact =
        withContext(Dispatchers.IO) {
            val from = rulesFor(cycleId)
            val to = candidate ?: getDraft()
            val slots = completions.getAllForCycle(cycleId).map { Triple(it.week, it.day, it.circuit) }
            val closed = days.getForCycle(cycleId).filter { it.closedAt != null }
                .map { Position(it.week, it.day) }.toSet()
            RuleImpact.analyse(from, to, slots, closed)
        }

    /** INVARIANT 7/8: re-snapshots [cycleId] from the current draft. Deletes no completions. */
    suspend fun applyDraftToCycle(cycleId: Long): List<String> = withContext(Dispatchers.IO) {
        val draft = getDraft()
        val errors = RuleValidation.validate(draft)
        if (errors.isNotEmpty()) return@withContext errors
        snapshotRules(cycleId, draft)
        emptyList()
    }

    /** Rewrites the draft to [ProgramRules.DEFAULT]. Does not touch any cycle. */
    suspend fun restoreDefaultRules() = withContext(Dispatchers.IO) {
        dao.upsert(defaultEntity())
    }

    /**
     * Writes [rules] (defaulting to the current draft) as [cycleId]'s frozen snapshot,
     * including the program's exercise composition at this moment (INVARIANT 7).
     */
    suspend fun snapshotRules(cycleId: Long, rules: ProgramRules? = null) =
        withContext(Dispatchers.IO) {
            val effective = rules ?: getDraft()
            val programIds = exercises.getActive()
                .filter { it.slot == "PROGRAM" && it.enabled }
                .sortedBy { it.sortOrder }
                .joinToString(",") { it.id }

            dao.upsertCycleRules(
                CycleRulesEntity(
                    cycleId = cycleId,
                    weeks = effective.weeks,
                    daysPerWeek = effective.daysPerWeek,
                    circuitsPerWeekCsv = ProgramRules.circuitsCsv(effective.circuitsPerWeek),
                    exercisesPerCircuit = effective.exercisesPerCircuit,
                    warmUpCount = effective.warmUpCount,
                    stretchCount = effective.stretchCount,
                    dayRolloverHour = effective.dayRolloverHour,
                    warmUpEnabled = effective.warmUpEnabled,
                    stretchEnabled = effective.stretchEnabled,
                    countRoutinesInTotals = effective.countRoutinesInTotals,
                    lockFutureDays = effective.lockFutureDays,
                    programExerciseIdsCsv = programIds,
                    snapshotAt = System.currentTimeMillis()
                )
            )
        }
}
