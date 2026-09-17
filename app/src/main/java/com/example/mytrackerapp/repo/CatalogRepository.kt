package com.example.mytrackerapp.repo

import com.example.mytrackerapp.data.db.ExerciseDao
import com.example.mytrackerapp.data.entity.ExerciseEntity
import com.example.mytrackerapp.data.seed.SeedData
import com.example.mytrackerapp.domain.CatalogValidation
import com.example.mytrackerapp.domain.ExerciseDraft
import com.example.mytrackerapp.domain.model.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Exercise catalog CRUD. Ids are permanent — completion rows point at them — so renaming
 * an exercise never changes its id, and removing one archives it (INVARIANT 8's catalog
 * equivalent) rather than deleting the row.
 */
class CatalogRepository(private val dao: ExerciseDao) {

    fun observeAll(includeArchived: Boolean = false): Flow<List<Exercise>> =
        (if (includeArchived) allEntitiesFlow() else dao.observeActive())
            .map { list -> list.map { it.toDomain() } }

    private fun allEntitiesFlow(): Flow<List<ExerciseEntity>> = dao.observeAll()

    /** Slug from the name, deduped with a numeric suffix if it collides. */
    suspend fun create(draft: ExerciseDraft): Result<String> = withContext(Dispatchers.IO) {
        val errors = CatalogValidation.validate(draft)
        if (errors.isNotEmpty()) return@withContext Result.failure(IllegalArgumentException(errors.first()))

        val id = uniqueId(slugify(draft.name))
        val maxSort = dao.getAll().maxOfOrNull { it.sortOrder } ?: 0
        dao.upsert(draft.toEntity(id = id, sortOrder = maxSort + 1, isCustom = true))
        Result.success(id)
    }

    /** Everything except the id is editable. */
    suspend fun update(id: String, draft: ExerciseDraft): Result<Unit> = withContext(Dispatchers.IO) {
        val errors = CatalogValidation.validate(draft)
        if (errors.isNotEmpty()) return@withContext Result.failure(IllegalArgumentException(errors.first()))

        val existing = dao.getById(id)
            ?: return@withContext Result.failure(NoSuchElementException("No exercise with id $id"))
        dao.upsert(
            draft.toEntity(
                id = id,
                sortOrder = existing.sortOrder,
                isCustom = existing.isCustom,
                archivedAt = existing.archivedAt
            )
        )
        Result.success(Unit)
    }

    /** Soft delete. Fails if it would leave the PROGRAM slot with no enabled exercise. */
    suspend fun archive(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val target = dao.getById(id)
            ?: return@withContext Result.failure(NoSuchElementException("No exercise with id $id"))
        if (target.slot == "PROGRAM" && target.enabled && target.archivedAt == null) {
            val remaining = dao.countBySlot("PROGRAM")
            if (remaining <= 1) {
                return@withContext Result.failure(
                    IllegalStateException("At least one program exercise must stay enabled.")
                )
            }
        }
        dao.archive(id, System.currentTimeMillis())
        Result.success(Unit)
    }

    suspend fun restore(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        dao.getById(id) ?: return@withContext Result.failure(NoSuchElementException("No exercise with id $id"))
        dao.restore(id)
        Result.success(Unit)
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val target = dao.getById(id)
            ?: return@withContext Result.failure(NoSuchElementException("No exercise with id $id"))
        if (!enabled && target.slot == "PROGRAM" && dao.countBySlot("PROGRAM") <= 1) {
            return@withContext Result.failure(
                IllegalStateException("At least one program exercise must stay enabled.")
            )
        }
        dao.setEnabled(id, enabled)
        Result.success(Unit)
    }

    /** Swaps sortOrder with the neighbour in the same slot. direction: -1 up, +1 down. */
    suspend fun move(id: String, direction: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val target = dao.getById(id)
            ?: return@withContext Result.failure(NoSuchElementException("No exercise with id $id"))
        val siblings = dao.getActive().filter { it.slot == target.slot }.sortedBy { it.sortOrder }
        val index = siblings.indexOfFirst { it.id == id }
        val swapWith = index + direction
        if (index < 0 || swapWith !in siblings.indices) return@withContext Result.success(Unit)

        val other = siblings[swapWith]
        dao.setSortOrder(target.id, other.sortOrder)
        dao.setSortOrder(other.id, target.sortOrder)
        Result.success(Unit)
    }

    /** Copies a row with a new id and " (copy)" appended. */
    suspend fun duplicate(id: String): Result<String> = withContext(Dispatchers.IO) {
        val target = dao.getById(id)
            ?: return@withContext Result.failure(NoSuchElementException("No exercise with id $id"))
        val newId = uniqueId(slugify("${target.name} copy"))
        val maxSort = dao.getAll().maxOfOrNull { it.sortOrder } ?: 0
        dao.upsert(
            target.copy(
                id = newId,
                name = "${target.name} (copy)",
                sortOrder = maxSort + 1,
                isCustom = true,
                archivedAt = null
            )
        )
        Result.success(newId)
    }

    /** Re-inserts the shipped catalog with REPLACE; custom rows are untouched. */
    suspend fun restoreDefaultCatalog() = withContext(Dispatchers.IO) {
        dao.insertAll(SeedData.ALL_EXERCISES)
    }

    private suspend fun uniqueId(base: String): String {
        var candidate = base
        var suffix = 2
        while (dao.getById(candidate) != null) {
            candidate = "${base}_${suffix++}"
        }
        return candidate
    }

    private fun slugify(name: String): String {
        val slug = name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return slug.ifEmpty { "exercise" }
    }
}

private fun ExerciseDraft.toEntity(
    id: String,
    sortOrder: Int,
    isCustom: Boolean,
    archivedAt: Long? = null
) = ExerciseEntity(
    id = id,
    name = name,
    category = category.name,
    muscles = muscles,
    instructions = instructions,
    targetType = targetType.name,
    targetValue = targetValue,
    perSide = perSide,
    targetLabel = targetLabel,
    videoUrl = videoUrl,
    sortOrder = sortOrder,
    slot = slot.name,
    enabled = enabled,
    archivedAt = archivedAt,
    isCustom = isCustom,
    tracksReps = tracksReps,
    tracksLoad = tracksLoad,
    defaultLoadKg = defaultLoadKg,
    defaultBandLevel = defaultBandLevel,
    progressionStep = progressionStep
)
