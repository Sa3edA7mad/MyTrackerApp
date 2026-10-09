package com.example.mytrackerapp.repo

import com.example.mytrackerapp.data.db.ProgramDao
import com.example.mytrackerapp.data.db.RulesDao
import com.example.mytrackerapp.data.entity.ProgramEntity
import com.example.mytrackerapp.data.entity.ProgramRulesEntity
import com.example.mytrackerapp.data.seed.StarterPrograms
import com.example.mytrackerapp.domain.CircuitPlan
import com.example.mytrackerapp.domain.PlanCodec
import com.example.mytrackerapp.domain.PlanValidation
import com.example.mytrackerapp.domain.ProgramPlan
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.model.Program
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun ProgramEntity.toDomain() = Program(id, name, active, archivedAt != null)

/** The programs table: create, rename, activate/pause, archive. Rules and plans live in [RulesRepository]. */
class ProgramRepository(private val programs: ProgramDao, private val rules: RulesDao) {

    /** Every program, archived ones included, in creation order. */
    fun observeAll(): Flow<List<Program>> = programs.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(): Flow<List<Program>> = programs.observeActive().map { list -> list.map { it.toDomain() } }

    fun observe(id: Long): Flow<Program?> = programs.observe(id).map { it?.toDomain() }

    /**
     * A new, paused program with one empty circuit. It can be activated once its plan
     * validates, so Today never shows a day that can't be trained.
     */
    suspend fun create(name: String): Result<Long> = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext Result.failure(IllegalArgumentException("Name can't be empty."))
        val now = System.currentTimeMillis()
        val id = programs.insert(ProgramEntity(name = name.trim(), active = false, createdAt = now))
        val units = rules.getRules(StarterPrograms.HOME_ID)
        val r = StarterPrograms.NEW_PROGRAM_RULES
        rules.upsert(
            ProgramRulesEntity(
                id = id.toInt(),
                weeks = r.weeks,
                daysPerWeek = r.daysPerWeek,
                circuitsPerWeekCsv = ProgramRules.circuitsCsv(r.circuitsPerWeek),
                dayRolloverHour = r.dayRolloverHour,
                warmUpEnabled = r.warmUpEnabled,
                stretchEnabled = r.stretchEnabled,
                countRoutinesInTotals = r.countRoutinesInTotals,
                lockFutureDays = r.lockFutureDays,
                weightUnit = units?.weightUnit ?: "KG",
                lengthUnit = units?.lengthUnit ?: "CM",
                updatedAt = now,
                planText = PlanCodec.encode(ProgramPlan(circuits = listOf(CircuitPlan("a", "Circuit A"))))
            )
        )
        Result.success(id)
    }

    suspend fun rename(id: Long, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext Result.failure(IllegalArgumentException("Name can't be empty."))
        programs.rename(id, name.trim())
        Result.success(Unit)
    }

    /** Activating requires a valid plan; pausing always works and keeps the cycle as it is. */
    suspend fun setActive(id: Long, active: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (active) {
            val plan = rules.getRules(id)?.let { PlanCodec.decode(it.planText) ?: ProgramPlan.SLOT_DEFAULT }
            val errors = plan?.let { PlanValidation.validate(it) } ?: listOf("This program has no plan.")
            if (errors.isNotEmpty()) return@withContext Result.failure(IllegalStateException(errors.first()))
        }
        programs.setActive(id, active)
        Result.success(Unit)
    }

    /** Every program with its draft rules and plan text, for the JSON export. */
    suspend fun exportAll(): JSONArray = withContext(Dispatchers.IO) {
        val out = JSONArray()
        programs.getAll().forEach { p ->
            val r = rules.getRules(p.id)
            out.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("active", p.active)
                    .put("archived", p.archivedAt != null)
                    .put("weeks", r?.weeks ?: JSONObject.NULL)
                    .put("daysPerWeek", r?.daysPerWeek ?: JSONObject.NULL)
                    .put("circuitsPerWeek", r?.circuitsPerWeekCsv ?: JSONObject.NULL)
                    .put("plan", r?.planText ?: JSONObject.NULL)
            )
        }
        out
    }

    suspend fun archive(id: Long) = withContext(Dispatchers.IO) {
        programs.archive(id, System.currentTimeMillis())
    }

    /** Un-archives, still paused. */
    suspend fun restore(id: Long) = withContext(Dispatchers.IO) {
        programs.archive(id, null)
    }
}
