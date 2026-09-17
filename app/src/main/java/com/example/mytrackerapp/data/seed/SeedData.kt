package com.example.mytrackerapp.data.seed

import com.example.mytrackerapp.data.entity.ExerciseEntity
import com.example.mytrackerapp.data.entity.MetricEntity

/**
 * Transcribed verbatim from Saeed_4Week.xlsx.
 *
 * Do not paraphrase, re-order, or "improve" any string. The separator in `muscles` is
 * U+00B7 MIDDLE DOT and the dash in the Crunch instruction is U+2014 EM DASH; both are
 * asserted in SeedTest so a mangled encoding fails the build rather than shipping.
 *
 * Nine of the workbook's video links are YouTube *search* URLs rather than specific
 * videos. That is how the sheet ships and it is preserved deliberately.
 */
object SeedData {

    private fun search(encodedName: String) =
        "https://www.youtube.com/results?search_query=${encodedName}%20exercise%20proper%20form"

    private const val BODYWEIGHT = "BODYWEIGHT"
    private const val BAND = "BAND"
    private const val WARMUP = "WARMUP"
    private const val STRETCH = "STRETCH"
    private const val REPS = "REPS"
    private const val SECONDS = "SECONDS"

    /** The 13 program exercises — identical in all four weeks. */
    private val MAIN = listOf(
        ExerciseEntity(
            id = "squat",
            name = "Squat",
            category = BODYWEIGHT,
            muscles = "Legs · Glutes · Core",
            instructions = "Feet shoulder-width, toes slightly out. Lower until thighs are parallel to floor. Keep chest up, weight through heels. Stand fully.",
            targetType = REPS,
            targetValue = 5,
            perSide = false,
            targetLabel = "5 reps",
            videoUrl = search("Squat"),
            sortOrder = 1
        ),
        ExerciseEntity(
            id = "push_up",
            name = "Push-up",
            category = BODYWEIGHT,
            muscles = "Chest · Shoulders · Triceps",
            instructions = "Hands shoulder-width, body in a straight line from head to heels. Lower chest to floor. Push up fully. If too hard: drop to knees.",
            targetType = REPS,
            targetValue = 5,
            perSide = false,
            targetLabel = "5 reps",
            videoUrl = "https://www.youtube.com/watch?v=WDIpL0pjun0",
            sortOrder = 2
        ),
        ExerciseEntity(
            id = "dead_hang",
            name = "Dead Hang",
            category = BODYWEIGHT,
            muscles = "Shoulders · Spine · Grip",
            instructions = "Hang from any bar with both hands, shoulder-width. Let body hang fully. Keep shoulders slightly pulled down. Breathe and hold.",
            targetType = SECONDS,
            targetValue = 15,
            perSide = false,
            targetLabel = "15 sec",
            videoUrl = search("Dead%20Hang"),
            sortOrder = 3
        ),
        ExerciseEntity(
            id = "crunch",
            name = "Crunch",
            category = BODYWEIGHT,
            muscles = "Core · Abs",
            instructions = "Lie on back, knees bent. Hands behind head lightly. Curl upper back off floor — only shoulders lift. Exhale up, inhale down. No neck pull.",
            targetType = REPS,
            targetValue = 8,
            perSide = false,
            targetLabel = "8 reps",
            videoUrl = search("Crunch"),
            sortOrder = 4
        ),
        ExerciseEntity(
            id = "superman",
            name = "Superman",
            category = BODYWEIGHT,
            muscles = "Lower Back · Glutes",
            instructions = "Lie face down, arms extended forward. Lift arms, chest, and legs at the same time. Squeeze glutes. Hold 2 sec at top. Lower slowly.",
            targetType = REPS,
            targetValue = 5,
            perSide = false,
            targetLabel = "5 reps",
            videoUrl = search("Superman"),
            sortOrder = 5
        ),
        ExerciseEntity(
            id = "glute_bridge",
            name = "Glute Bridge",
            category = BODYWEIGHT,
            muscles = "Glutes · Hamstrings · Core",
            instructions = "Lie on back, knees bent, feet flat near glutes. Drive hips up by squeezing glutes. Hold 2 sec at top. Lower slowly. Do not arch lower back.",
            targetType = REPS,
            targetValue = 8,
            perSide = false,
            targetLabel = "8 reps",
            videoUrl = "https://www.youtube.com/watch?v=h5UOyrVYAhs",
            sortOrder = 6
        ),
        ExerciseEntity(
            id = "bicep_curl",
            name = "Bicep Curl",
            category = BAND,
            muscles = "Biceps",
            instructions = "Stand on band, feet shoulder-width, hold ends with palms up. Curl toward shoulders. Keep elbows at sides. Lower slowly.",
            targetType = REPS,
            targetValue = 8,
            perSide = false,
            targetLabel = "8 reps",
            videoUrl = search("Bicep%20Curl"),
            sortOrder = 7
        ),
        ExerciseEntity(
            id = "tricep_pushdown",
            name = "Tricep Pushdown",
            category = BAND,
            muscles = "Triceps",
            instructions = "Anchor band above head on door or bar. Hold with palms down. Push down until arms straight. Keep elbows fixed at sides. Control return.",
            targetType = REPS,
            targetValue = 8,
            perSide = false,
            targetLabel = "8 reps",
            videoUrl = search("Tricep%20Pushdown"),
            sortOrder = 8
        ),
        ExerciseEntity(
            id = "pull_apart",
            name = "Pull-Apart",
            category = BAND,
            muscles = "Rear Delts · Upper Back",
            instructions = "Hold band in front at shoulder height, hands shoulder-width. Pull band apart squeezing shoulder blades. Arms stay straight. Return slowly.",
            targetType = REPS,
            targetValue = 10,
            perSide = false,
            targetLabel = "10 reps",
            videoUrl = "https://www.youtube.com/watch?v=8voEpUl22wY",
            sortOrder = 9
        ),
        ExerciseEntity(
            id = "external_rotation",
            name = "External Rotation",
            category = BAND,
            muscles = "Rotator Cuff",
            instructions = "Anchor band at elbow height. Elbow bent 90 degrees at side. Rotate forearm outward away from body. Keep elbow fixed at ribs. Return slowly.",
            targetType = REPS,
            targetValue = 10,
            perSide = true,
            targetLabel = "10 reps each side",
            videoUrl = search("External%20Rotation"),
            sortOrder = 10
        ),
        ExerciseEntity(
            id = "internal_rotation",
            name = "Internal Rotation",
            category = BAND,
            muscles = "Rotator Cuff",
            instructions = "Anchor band at elbow height. Elbow bent 90 degrees at side. Rotate forearm inward toward body. Keep elbow fixed at ribs. Return slowly.",
            targetType = REPS,
            targetValue = 10,
            perSide = true,
            targetLabel = "10 reps each side",
            videoUrl = search("Internal%20Rotation"),
            sortOrder = 11
        ),
        ExerciseEntity(
            id = "shoulder_press",
            name = "Shoulder Press",
            category = BAND,
            muscles = "Shoulders · Triceps",
            instructions = "Stand on band, hold ends at shoulder height, palms forward. Press straight up overhead until arms fully extended. Lower slowly.",
            targetType = REPS,
            targetValue = 8,
            perSide = false,
            targetLabel = "8 reps",
            videoUrl = search("Shoulder%20Press"),
            sortOrder = 12
        ),
        ExerciseEntity(
            id = "band_row",
            name = "Band Row",
            category = BAND,
            muscles = "Back · Biceps · Rear Delts",
            instructions = "Anchor band at waist height. Hold with both hands, step back for tension. Pull hands toward torso squeezing shoulder blades. Return slowly.",
            targetType = REPS,
            targetValue = 10,
            perSide = false,
            targetLabel = "10 reps",
            videoUrl = "https://www.youtube.com/watch?v=LSkyinhmA8k",
            sortOrder = 13
        )
    )

    /**
     * Warm-up, before the first circuit of the day.
     * The sheet gives no muscle column for these, so `muscles` is deliberately empty.
     * The "each way / each leg / each foot" nuance lives in `targetLabel` only —
     * these are self-paced rep moves, not two-stage per-side exercises.
     */
    private val WARM_UP = listOf(
        ExerciseEntity(
            id = "neck_rolls",
            name = "Neck Rolls",
            category = WARMUP,
            muscles = "",
            instructions = "Slowly roll head in full circles, both directions. Keep shoulders relaxed.",
            targetType = REPS,
            targetValue = 5,
            perSide = false,
            targetLabel = "5 each way",
            videoUrl = search("Neck%20Rolls"),
            sortOrder = 101
        ),
        ExerciseEntity(
            id = "shoulder_rolls",
            name = "Shoulder Rolls",
            category = WARMUP,
            muscles = "",
            instructions = "Roll both shoulders forward 5 times then backward 5 times. Big slow circles.",
            targetType = REPS,
            targetValue = 10,
            perSide = false,
            targetLabel = "10 total",
            videoUrl = search("Shoulder%20Rolls"),
            sortOrder = 102
        ),
        ExerciseEntity(
            id = "arm_circles",
            name = "Arm Circles",
            category = WARMUP,
            muscles = "",
            instructions = "Arms out to sides. Small circles then bigger. Forward then backward.",
            targetType = REPS,
            targetValue = 10,
            perSide = false,
            targetLabel = "10 each way",
            videoUrl = search("Arm%20Circles"),
            sortOrder = 103
        ),
        ExerciseEntity(
            id = "hip_circles",
            name = "Hip Circles",
            category = WARMUP,
            muscles = "",
            instructions = "Hands on hips, feet shoulder-width. Rotate hips in large circles both ways.",
            targetType = REPS,
            targetValue = 10,
            perSide = false,
            targetLabel = "10 each way",
            videoUrl = search("Hip%20Circles"),
            sortOrder = 104
        ),
        ExerciseEntity(
            id = "leg_swings",
            name = "Leg Swings",
            category = WARMUP,
            muscles = "",
            instructions = "Hold wall for balance. Swing one leg forward and back, then side to side. Switch.",
            targetType = REPS,
            targetValue = 10,
            perSide = false,
            targetLabel = "10 each leg",
            videoUrl = search("Leg%20Swings"),
            sortOrder = 105
        ),
        ExerciseEntity(
            id = "ankle_rolls",
            name = "Ankle Rolls",
            category = WARMUP,
            muscles = "",
            instructions = "Lift one foot. Rotate ankle slowly in full circles. Switch feet.",
            targetType = REPS,
            targetValue = 10,
            perSide = false,
            targetLabel = "10 each foot",
            videoUrl = search("Ankle%20Rolls"),
            sortOrder = 106
        ),
        ExerciseEntity(
            id = "cat_cow",
            name = "Cat-Cow",
            category = WARMUP,
            muscles = "",
            instructions = "On hands and knees. Arch back up (cat), then let belly drop (cow). Slow.",
            targetType = REPS,
            targetValue = 8,
            perSide = false,
            targetLabel = "8 cycles",
            videoUrl = search("Cat-Cow"),
            sortOrder = 107
        ),
        ExerciseEntity(
            id = "easy_air_squat",
            name = "Easy Air Squat",
            category = WARMUP,
            muscles = "",
            instructions = "Slow warm-up squats. Hold the bottom for 1 second. No weight.",
            targetType = REPS,
            targetValue = 8,
            perSide = false,
            targetLabel = "8 reps",
            videoUrl = search("Easy%20Air%20Squat"),
            sortOrder = 108
        )
    )

    /** Stretching, after the last circuit of the day. All timed holds. */
    private val STRETCHES = listOf(
        ExerciseEntity(
            id = "quad_stretch",
            name = "Quad Stretch",
            category = STRETCH,
            muscles = "Quads",
            instructions = "Stand on one leg, pull opposite foot toward glute. Hold. Use wall for balance. Switch sides.",
            targetType = SECONDS,
            targetValue = 30,
            perSide = true,
            targetLabel = "30 sec each",
            videoUrl = search("Quad%20Stretch"),
            sortOrder = 201
        ),
        ExerciseEntity(
            id = "hamstring_stretch",
            name = "Hamstring Stretch",
            category = STRETCH,
            muscles = "Hamstrings",
            instructions = "Lie on back. Lift one leg straight. Hold behind thigh or calf. Keep other leg flat. Switch sides.",
            targetType = SECONDS,
            targetValue = 30,
            perSide = true,
            targetLabel = "30 sec each",
            videoUrl = search("Hamstring%20Stretch"),
            sortOrder = 202
        ),
        ExerciseEntity(
            id = "hip_flexor_stretch",
            name = "Hip Flexor Stretch",
            category = STRETCH,
            muscles = "Hip Flexors",
            instructions = "Kneel on one knee in a lunge. Push hips forward gently. Feel stretch in front of rear hip. Keep torso upright.",
            targetType = SECONDS,
            targetValue = 30,
            perSide = true,
            targetLabel = "30 sec each",
            videoUrl = search("Hip%20Flexor%20Stretch"),
            sortOrder = 203
        ),
        ExerciseEntity(
            id = "chest_opener_band",
            name = "Chest Opener (Band)",
            category = STRETCH,
            muscles = "Chest · Front Shoulders",
            instructions = "Hold band behind back with both hands. Squeeze shoulder blades. Lift arms slightly. Feel stretch across chest.",
            targetType = SECONDS,
            targetValue = 30,
            perSide = false,
            targetLabel = "30 sec",
            videoUrl = search("Chest%20Opener%20%28Band%29"),
            sortOrder = 204
        ),
        ExerciseEntity(
            id = "lat_stretch",
            name = "Lat Stretch",
            category = STRETCH,
            muscles = "Lats · Side Body",
            instructions = "Reach one arm overhead and lean to opposite side. Feel stretch along your side.",
            targetType = SECONDS,
            targetValue = 30,
            perSide = true,
            targetLabel = "30 sec each",
            videoUrl = search("Lat%20Stretch"),
            sortOrder = 205
        ),
        ExerciseEntity(
            id = "shoulder_cross_band",
            name = "Shoulder Cross (Band)",
            category = STRETCH,
            muscles = "Rear Shoulder · Rotator Cuff",
            instructions = "Loop band around wrist. Cross arm across chest. Use other arm to press upper arm gently toward body. Feel rear shoulder stretch.",
            targetType = SECONDS,
            targetValue = 30,
            perSide = true,
            targetLabel = "30 sec each",
            videoUrl = search("Shoulder%20Cross%20%28Band%29"),
            sortOrder = 206
        ),
        ExerciseEntity(
            id = "childs_pose",
            name = "Child's Pose",
            category = STRETCH,
            muscles = "Lower Back · Hips",
            instructions = "Kneel and sit back toward heels. Reach arms forward on floor. Let head drop. Breathe deeply.",
            targetType = SECONDS,
            targetValue = 45,
            perSide = false,
            targetLabel = "45 sec",
            videoUrl = search("Child%27s%20Pose"),
            sortOrder = 207
        ),
        ExerciseEntity(
            id = "spinal_twist",
            name = "Spinal Twist",
            category = STRETCH,
            muscles = "Spine · Glutes",
            instructions = "Lie on back. Bring one knee to chest, rotate it across body. Look the other way. Switch sides.",
            targetType = SECONDS,
            targetValue = 30,
            perSide = true,
            targetLabel = "30 sec each",
            videoUrl = search("Spinal%20Twist"),
            sortOrder = 208
        )
    )

    /** 13 + 8 + 8 = 29, with `slot` derived from `category` (T12: `slot` is what decides
     *  circuit membership going forward; `category` stays as the display badge). */
    val ALL_EXERCISES: List<ExerciseEntity> = (MAIN + WARM_UP + STRETCHES).map {
        it.copy(
            slot = when (it.category) {
                WARMUP -> "WARMUP"
                STRETCH -> "STRETCH"
                else -> "PROGRAM"
            }
        )
    }

    private const val WEIGHT = "WEIGHT"
    private const val LENGTH = "LENGTH"
    private const val PERCENT = "PERCENT"
    private const val COUNT = "COUNT"

    /**
     * The default measurement catalog — 17 rows, 7 enabled out of the box. All 17 exist
     * from day one so turning one on later needs no migration.
     */
    val DEFAULT_METRICS: List<MetricEntity> = listOf(
        MetricEntity("bodyweight", "Body weight", WEIGHT, "Same time of day, before eating", true, false, 1, 1),
        MetricEntity("body_fat", "Body fat", PERCENT, "From a scale or calipers — consistency matters more than accuracy", true, false, 1, 2),
        MetricEntity("height", "Height", LENGTH, "Needed for BMI. Measure once", true, false, 0, 3),
        MetricEntity("neck", "Neck", LENGTH, "Just below the Adam's apple", false, false, 1, 4),
        MetricEntity("shoulders", "Shoulders", LENGTH, "Widest point, arms relaxed", false, false, 1, 5),
        MetricEntity("chest", "Chest", LENGTH, "At nipple line, at the end of a normal breath out", true, false, 1, 6),
        MetricEntity("upper_arm_left", "Upper arm (L)", LENGTH, "Flexed, at the biggest point", false, false, 1, 7),
        MetricEntity("upper_arm_right", "Upper arm (R)", LENGTH, "Flexed, at the biggest point", true, false, 1, 8),
        MetricEntity("forearm_left", "Forearm (L)", LENGTH, "Widest point below the elbow", false, false, 1, 9),
        MetricEntity("forearm_right", "Forearm (R)", LENGTH, "Widest point below the elbow", false, false, 1, 10),
        MetricEntity("waist", "Waist", LENGTH, "At the navel, don't hold it in", true, false, 1, 11),
        MetricEntity("hips", "Hips", LENGTH, "Widest point of the glutes", true, false, 1, 12),
        MetricEntity("thigh_left", "Thigh (L)", LENGTH, "Mid-thigh, standing relaxed", false, false, 1, 13),
        MetricEntity("thigh_right", "Thigh (R)", LENGTH, "Mid-thigh, standing relaxed", true, false, 1, 14),
        MetricEntity("calf_left", "Calf (L)", LENGTH, "Widest point, standing", false, false, 1, 15),
        MetricEntity("calf_right", "Calf (R)", LENGTH, "Widest point, standing", false, false, 1, 16),
        MetricEntity("resting_hr", "Resting heart rate", COUNT, "First thing in the morning, before getting up", true, false, 0, 17)
    )
}
