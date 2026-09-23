package com.example.mytrackerapp.domain

import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.ExerciseSlot
import com.example.mytrackerapp.domain.model.TargetType

/** Everything editable about an exercise. Mirrors [com.example.mytrackerapp.domain.model.Exercise]
 *  minus the id, which is permanent — completion rows point at it, so renaming an
 *  exercise never changes its id. */
data class ExerciseDraft(
    val name: String,
    val category: Category,
    val slot: ExerciseSlot,
    val muscles: String,
    val instructions: String,
    val targetType: TargetType,
    val targetValue: Int,
    val perSide: Boolean,
    val targetLabel: String,
    val videoUrl: String,
    val tracksReps: Boolean = false,
    val tracksLoad: Boolean = false,
    val defaultLoadKg: Double? = null,
    val defaultBandLevel: String? = null,
    val progressionStep: Int = 0,
    val enabled: Boolean = true
)

object CatalogValidation {

    const val MAX_NAME_LENGTH = 60
    const val MAX_TARGET_VALUE = 999
    const val MAX_PROGRESSION_STEP = 50

    fun validate(draft: ExerciseDraft): List<String> {
        val errors = mutableListOf<String>()
        if (draft.name.isBlank()) errors += "Name can't be empty."
        if (draft.name.length > MAX_NAME_LENGTH) {
            errors += "Name must be $MAX_NAME_LENGTH characters or fewer."
        }
        if (draft.targetValue !in 1..MAX_TARGET_VALUE) {
            errors += "Target must be between 1 and $MAX_TARGET_VALUE."
        }
        if (draft.videoUrl.isNotBlank() &&
            !draft.videoUrl.startsWith("http://") &&
            !draft.videoUrl.startsWith("https://")
        ) {
            errors += "Video URL must start with http:// or https://, or be left blank."
        }
        if (draft.progressionStep !in 0..MAX_PROGRESSION_STEP) {
            errors += "Progression step must be between 0 and $MAX_PROGRESSION_STEP."
        }
        return errors
    }
}
