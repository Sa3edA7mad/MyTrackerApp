package com.example.mytrackerapp.repo

import com.example.mytrackerapp.data.db.CompletionDao
import com.example.mytrackerapp.data.db.DayDao
import com.example.mytrackerapp.data.db.ExerciseDao
import com.example.mytrackerapp.data.db.RulesDao
import com.example.mytrackerapp.data.entity.CycleRulesEntity
import com.example.mytrackerapp.data.entity.ProgramRulesEntity
import com.example.mytrackerapp.data.seed.StarterPrograms
import com.example.mytrackerapp.domain.ApplyImpact
import com.example.mytrackerapp.domain.PlanCodec
import com.example.mytrackerapp.domain.PlanValidation
import com.example.mytrackerapp.domain.Position
import com.example.mytrackerapp.domain.ProgramPlan
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.resolve
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
    // exercisesPerCircuit/warmUpCount/stretchCount/circuitSizes are left at their defaults
    // here; the draft's real values are filled in by RulesRepository from its plan.
)

/** The draft plan. Blank text is the original catalog-slot plan (Home). */
fun ProgramRulesEntity.plan(): ProgramPlan = PlanCodec.decode(planText) ?: ProgramPlan.SLOT_DEFAULT

/**
 * The plan frozen with a cycle. Null only for a cycle that predates snapshots entirely,
 * which reads the live catalog slots instead.
 */
fun CycleRulesEntity.plan(): ProgramPlan? = PlanCodec.decode(planText)
    ?: programExerciseIdsCsv.split(",").filter { it.isNotBlank() }
        .takeIf { it.isNotEmpty() }?.let { ProgramPlan.legacy(it) }

fun ProgramRulesEntity.toUnitPrefs(): UnitPrefs = UnitPrefs(
    weight = runCatching { WeightUnit.valueOf(weightUnit) }.getOrDefault(WeightUnit.KG),
    length = runCatching { LengthUnit.valueOf(lengthUnit) }.getOrDefault(LengthUnit.CM)
)

fun CycleRulesEntity.toDomain(): ProgramRules {
    // Snapshots from before programs carry no plan text: one uniform circuit, as they always read.
    val plan = PlanCodec.decode(planText)
    return ProgramRules(
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
        lockFutureDays = lockFutureDays,
        circuitSizes = plan?.circuits?.map { it.size },
        dayRotations = plan?.dayRotations().orEmpty()
    )
}

/**
 * Rule and plan read/write, snapshot, and apply-to-cycle for one program. The only writer
 * of `program_rules` and `cycle_rules`.
 *
 * @param programId whose rules row this reads and writes (1 = Home, the default).
 */
class RulesRepository(
    private val dao: RulesDao,
    private val exercises: ExerciseDao,
    private val days: DayDao,
    private val completions: CompletionDao,
    val programId: Long = StarterPrograms.HOME_ID
) {

    /** The editable draft, with the plan-derived counts filled in. */
    fun observeDraft(): Flow<ProgramRules> = dao.observeRules(programId).map { entity ->
        (entity ?: defaultEntity()).let { it.toDomain().withDerivedCounts(it.plan()) }
    }

    suspend fun getDraft(): ProgramRules = withContext(Dispatchers.IO) {
        (dao.getRules(programId) ?: defaultEntity()).let { it.toDomain().withDerivedCounts(it.plan()) }
    }

    /** Units are app-wide, so any program's row answers for all of them. */
    fun observeUnits(): Flow<UnitPrefs> =
        dao.observeRules(programId).map { (it ?: defaultEntity()).toUnitPrefs() }

    /* ------------------------------------------------------------------ plan */

    /** The draft plan, unresolved — slot-backed circuits stay slot-backed. */
    fun observePlan(): Flow<ProgramPlan> =
        dao.observeRules(programId).map { (it ?: defaultEntity()).plan() }

    suspend fun getPlan(): ProgramPlan = withContext(Dispatchers.IO) {
        (dao.getRules(programId) ?: defaultEntity()).plan()
    }

    /**
     * Saves the draft plan and returns what is still wrong with it. An unfinished plan saves —
     * it is a draft, built a step at a time — but cannot be activated or applied to a cycle.
     * Like rules, a running cycle keeps its snapshot until applied.
     */
    suspend fun savePlan(plan: ProgramPlan): List<String> = withContext(Dispatchers.IO) {
        if (dao.getRules(programId) == null) dao.upsert(defaultEntity())
        dao.setPlan(programId, PlanCodec.encode(plan), System.currentTimeMillis())
        PlanValidation.validate(plan)
    }

    /** The frozen plan text of [cycleId], for the export. Blank for cycles that predate plans. */
    suspend fun cyclePlanText(cycleId: Long): String = withContext(Dispatchers.IO) {
        dao.getCycleRules(cycleId)?.planText.orEmpty()
    }

    /** The plan a cycle was snapshotted with (INVARIANT 7); null predates snapshots. */
    fun observeCyclePlan(cycleId: Long): Flow<ProgramPlan?> =
        dao.observeCycleRules(cycleId).map { it?.plan() }

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

    /** Fills the circuit shape and routine counts from [plan] resolved against the live catalog. */
    private suspend fun ProgramRules.withDerivedCounts(plan: ProgramPlan): ProgramRules {
        val resolved = plan.resolve(exercises.getAll().map { it.toDomain() })
        val sizes = resolved.circuits.map { it.size }
        return copy(
            exercisesPerCircuit = sizes.firstOrNull() ?: 0,
            warmUpCount = resolved.warmUp.size,
            stretchCount = resolved.stretch.size,
            circuitSizes = sizes,
            dayRotations = resolved.dayRotations()
        )
    }

    private fun defaultEntity(): ProgramRulesEntity {
        val d = StarterPrograms.defaultRulesFor(programId)
        return ProgramRulesEntity(
            id = programId.toInt(),
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

            val planText = dao.getRules(programId)?.planText.orEmpty()
            dao.upsert(
                ProgramRulesEntity(
                    id = programId.toInt(),
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
                    updatedAt = System.currentTimeMillis(),
                    planText = planText
                )
            )
            dao.setUnits(units.weight.name, units.length.name)
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
            val dayRows = days.getForCycle(cycleId)
            val closed = dayRows.filter { it.closedAt != null }
                .map { Position(it.week, it.day) }.toSet()
            val stretchDone = dayRows.filter { it.stretchDoneAt != null }
                .map { Position(it.week, it.day) }.toSet()
            RuleImpact.analyse(from, to, slots, stretchDone, closed)
        }

    /** INVARIANT 7/8: re-snapshots [cycleId] from the current draft. Deletes no completions. */
    suspend fun applyDraftToCycle(cycleId: Long): List<String> = withContext(Dispatchers.IO) {
        val draft = getDraft()
        val errors = RuleValidation.validate(draft) + PlanValidation.validate(getPlan())
        if (errors.isNotEmpty()) return@withContext errors
        snapshotRules(cycleId, draft)
        emptyList()
    }

    /**
     * Rewrites the draft rules to this program's defaults and the units to kg/cm. Keeps the
     * plan — circuits are edited, not "restored". Does not touch any cycle.
     */
    suspend fun restoreDefaultRules() = withContext(Dispatchers.IO) {
        val planText = dao.getRules(programId)?.planText.orEmpty()
        dao.upsert(defaultEntity().copy(planText = planText))
        dao.setUnits(WeightUnit.KG.name, LengthUnit.CM.name)
    }

    /**
     * Writes [rules] (defaulting to the current draft) as [cycleId]'s frozen snapshot,
     * including the program's resolved circuit plan at this moment (INVARIANT 7).
     */
    suspend fun snapshotRules(cycleId: Long, rules: ProgramRules? = null) =
        withContext(Dispatchers.IO) {
            val effective = rules ?: getDraft()
            val resolved = getPlan().resolve(exercises.getAll().map { it.toDomain() })
            val programIds = resolved.circuits.firstOrNull()?.items.orEmpty()
                .joinToString(",") { it.exerciseId }

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
                    snapshotAt = System.currentTimeMillis(),
                    planText = PlanCodec.encode(resolved)
                )
            )
        }
}
