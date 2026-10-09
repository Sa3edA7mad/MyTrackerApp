package com.example.mytrackerapp.data.seed

import com.example.mytrackerapp.domain.CircuitFormat
import com.example.mytrackerapp.domain.CircuitItem
import com.example.mytrackerapp.domain.CircuitOrder
import com.example.mytrackerapp.domain.CircuitPlan
import com.example.mytrackerapp.domain.ProgramPlan
import com.example.mytrackerapp.domain.ProgramRules

/**
 * The programs shipped alongside Home. Both start inactive — they are examples to edit,
 * activate or archive. Ids are fixed so the seed and MIGRATION_6_7 agree (INVARIANT 9).
 */
object StarterPrograms {

    data class Starter(val id: Long, val name: String, val rules: ProgramRules, val plan: ProgramPlan)

    const val HOME_ID = 1L
    const val HOME_NAME = "Home 4-Week"

    /** Three different days a week, one pass through that day's circuit. */
    private val THREE_DAY = ProgramRules(daysPerWeek = 3, circuitsPerWeek = listOf(1, 1, 1, 1))

    private fun items(vararg pairs: Pair<String, Int>) = pairs.map { (id, sets) -> CircuitItem(id, sets) }

    val GYM = Starter(
        id = 2,
        name = "Gym Strength",
        rules = THREE_DAY,
        plan = ProgramPlan(
            circuits = listOf(
                CircuitPlan(
                    "a", "Squat + Bench",
                    items("barbell_back_squat" to 5, "barbell_bench_press" to 5, "plank" to 3),
                    order = CircuitOrder.STRAIGHT, restSeconds = 120
                ),
                CircuitPlan(
                    "b", "Deadlift + Pull",
                    items("conventional_deadlift" to 3, "pull_up" to 4, "barbell_row" to 4, "hanging_knee_raise" to 3),
                    order = CircuitOrder.STRAIGHT, restSeconds = 120
                ),
                CircuitPlan(
                    "c", "Press + Accessories",
                    items(
                        "standing_overhead_press" to 4, "bulgarian_split_squat" to 3, "seated_cable_row" to 3,
                        "dumbbell_biceps_curl" to 3, "triceps_rope_pushdown" to 3
                    ),
                    order = CircuitOrder.STRAIGHT, restSeconds = 90
                )
            ),
            days = listOf(listOf("a"), listOf("b"), listOf("c")),
            warmUp = CircuitPlan(
                ProgramPlan.WARMUP_KEY, "Warm-up",
                items("cat_cow" to 1, "worlds_greatest_stretch" to 1, "shoulder_dislocates" to 1, "deep_squat_hold" to 1)
            ),
            stretch = CircuitPlan(
                ProgramPlan.STRETCH_KEY, "Stretch",
                items("couch_stretch" to 1, "pigeon_pose" to 1, "doorway_pec_stretch" to 1, "childs_pose" to 1)
            )
        )
    )

    val CROSSFIT = Starter(
        id = 3,
        name = "CrossFit Conditioning",
        rules = THREE_DAY,
        plan = ProgramPlan(
            circuits = listOf(
                CircuitPlan(
                    "a", "EMOM 12",
                    items("kettlebell_swing" to 4, "burpee" to 4, "wall_ball_shot" to 4),
                    format = CircuitFormat.EMOM, rounds = 12
                ),
                CircuitPlan(
                    "b", "AMRAP 15",
                    items("thruster" to 1, "box_jump" to 1, "toes_to_bar" to 1),
                    format = CircuitFormat.AMRAP, minutes = 15
                ),
                CircuitPlan(
                    "c", "Intervals 40/20",
                    // The bike's 60-second library default is overridden to the 40-second work period.
                    listOf(CircuitItem("assault_bike", 4, target = 40), CircuitItem("mountain_climber", 4)),
                    format = CircuitFormat.INTERVAL, workSeconds = 40, restSeconds = 20, rounds = 8
                )
            ),
            days = listOf(listOf("a"), listOf("b"), listOf("c")),
            warmUp = CircuitPlan(
                ProgramPlan.WARMUP_KEY, "Warm-up",
                items(
                    "cat_cow" to 1, "worlds_greatest_stretch" to 1, "ankle_dorsiflexion_wall_drill" to 1,
                    "deep_squat_hold" to 1
                )
            ),
            stretch = CircuitPlan(
                ProgramPlan.STRETCH_KEY, "Stretch",
                items("couch_stretch" to 1, "pigeon_pose" to 1, "lat_stretch_on_foam_roller" to 1, "childs_pose" to 1)
            )
        )
    )

    val ALL = listOf(GYM, CROSSFIT)

    /** Rules a brand-new program starts with: like the starters, three days of one circuit. */
    val NEW_PROGRAM_RULES = THREE_DAY

    /** "Restore defaults" target for [programId]'s rules. Home keeps the shipped 4-week shape. */
    fun defaultRulesFor(programId: Long): ProgramRules =
        if (programId == HOME_ID) ProgramRules.DEFAULT
        else ALL.firstOrNull { it.id == programId }?.rules ?: NEW_PROGRAM_RULES
}
