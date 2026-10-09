package com.example.mytrackerapp.domain

import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.TargetType
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A program's circuits, which circuits each day runs, and its warm-up and stretch.
 *
 * Pure Kotlin — no Android imports. Stored as [PlanCodec] text: the draft on `program_rules`,
 * and a resolved copy frozen on `cycle_rules` (INVARIANT 7).
 */

/** Guided order for a circuit's sets. */
enum class CircuitOrder {
    /** Squat 1, Bench 1, Squat 2, Bench 2 — circuit / conditioning style. */
    ROUNDS,
    /** Every Squat set, then every Bench set — gym style. */
    STRAIGHT
}

enum class CircuitFormat { STANDARD, EMOM, INTERVAL, AMRAP, FOR_TIME }

/** One exercise in a circuit. A null [target]/[loadKg] means "use the library default". */
data class CircuitItem(
    val exerciseId: String,
    val sets: Int = 1,
    val target: Int? = null,
    val loadKg: Double? = null
)

/**
 * A named circuit.
 *
 * When [slot] is set the circuit holds every enabled, non-archived exercise in that catalog
 * slot, in Library order — the original Home program, so editing the Library still edits
 * Home. Otherwise [items] is the explicit list.
 */
data class CircuitPlan(
    val key: String,
    val name: String,
    val items: List<CircuitItem> = emptyList(),
    val slot: String? = null,
    val order: CircuitOrder = CircuitOrder.ROUNDS,
    val format: CircuitFormat = CircuitFormat.STANDARD,
    /** Rest countdown between sets in guided mode; the rest period for INTERVAL. */
    val restSeconds: Int = 0,
    /** INTERVAL work period. */
    val workSeconds: Int = 0,
    /** EMOM minutes, INTERVAL rounds. */
    val rounds: Int = 0,
    /** AMRAP length, FOR_TIME cap (0 = no cap), in minutes. */
    val minutes: Int = 0
) {
    /** Completions this circuit needs: one per set of each item. */
    val size: Int get() = items.sumOf { it.sets }
}

data class ProgramPlan(
    val circuits: List<CircuitPlan>,
    /** Per day of the week, the keys of the circuits it runs. A missing or empty entry
     *  means "every circuit, in order". */
    val days: List<List<String>> = emptyList(),
    val warmUp: CircuitPlan = CircuitPlan(WARMUP_KEY, "Warm-up"),
    val stretch: CircuitPlan = CircuitPlan(STRETCH_KEY, "Stretch")
) {
    /** Indices into [circuits] for each listed day — the shape [ProgramRules] counts with. */
    fun dayRotations(): List<List<Int>> = days.map { keys ->
        keys.mapNotNull { key -> circuits.indexOfFirst { it.key == key }.takeIf { it >= 0 } }
    }

    companion object {
        const val WARMUP_KEY = "warmup"
        const val STRETCH_KEY = "stretch"

        /** Home before programs existed: one circuit of the PROGRAM slot, slot routines. */
        val SLOT_DEFAULT = ProgramPlan(
            circuits = listOf(CircuitPlan("main", "Circuit", slot = "PROGRAM")),
            warmUp = CircuitPlan(WARMUP_KEY, "Warm-up", slot = "WARMUP"),
            stretch = CircuitPlan(STRETCH_KEY, "Stretch", slot = "STRETCH")
        )

        /** A cycle snapshotted before programs: one circuit of exactly these ids. */
        fun legacy(programIds: List<String>) = ProgramPlan(
            circuits = listOf(CircuitPlan("main", "Circuit", programIds.map { CircuitItem(it) }))
        )
    }
}

/**
 * Turns slot-backed circuits into explicit item lists against [catalog]. Explicit circuits
 * drop items whose exercise is archived, disabled or missing — the catalog equivalent of an
 * orphan (INVARIANT 8). This is what a cycle snapshot freezes.
 */
fun ProgramPlan.resolve(catalog: List<Exercise>): ProgramPlan {
    val live = catalog.filter { it.enabled && !it.isArchived }
    val liveIds = live.map { it.id }.toSet()
    fun CircuitPlan.resolved(): CircuitPlan = when (val s = slot) {
        null -> copy(items = items.filter { it.exerciseId in liveIds })
        else -> copy(
            slot = null,
            items = live.filter { it.slot.name == s }.sortedBy { it.sortOrder }.map { CircuitItem(it.id) }
        )
    }
    return copy(
        circuits = circuits.map { it.resolved() },
        warmUp = warmUp.resolved(),
        stretch = stretch.resolved()
    )
}

/* -------------------------------------------------------------------- steps */

/**
 * One tick in a circuit: an exercise's set. [key] is the exercise id for a single-set item,
 * and `id#set` otherwise, so a single-set circuit (every Home circuit) keys exactly as it
 * did before sets existed. Exercise ids are slugs (`[a-z0-9_]`), so `#` never collides.
 */
data class CircuitStep(val key: String, val exercise: Exercise, val set: Int, val sets: Int)

fun stepKey(exerciseId: String, set: Int, sets: Int): String =
    if (sets <= 1) exerciseId else "$exerciseId#$set"

/** (exerciseId, setNumber) for a [CircuitStep.key]. */
fun parseStepKey(key: String): Pair<String, Int> {
    val hash = key.lastIndexOf('#')
    if (hash < 0) return key to 1
    return key.substring(0, hash) to (key.substring(hash + 1).toIntOrNull() ?: 1)
}

/**
 * Expands a resolved circuit into the ordered ticks guided and list mode walk through,
 * applying the item's target/load override to the exercise it shows.
 */
fun CircuitPlan.steps(catalog: Map<String, Exercise>): List<CircuitStep> {
    val entries = items.mapNotNull { item -> catalog[item.exerciseId]?.let { item to it.withOverride(item) } }
    val ordered = when (order) {
        CircuitOrder.STRAIGHT -> entries.flatMap { (item, ex) -> (1..item.sets).map { Triple(item, ex, it) } }
        CircuitOrder.ROUNDS -> {
            val most = entries.maxOfOrNull { it.first.sets } ?: 0
            (1..most).flatMap { set ->
                entries.filter { it.first.sets >= set }.map { (item, ex) -> Triple(item, ex, set) }
            }
        }
    }
    return ordered.map { (item, ex, set) ->
        CircuitStep(stepKey(ex.id, set, item.sets), ex, set, item.sets)
    }
}

private fun Exercise.withOverride(item: CircuitItem): Exercise {
    val target = item.target ?: return if (item.loadKg != null) copy(defaultLoadKg = item.loadKg) else this
    val unit = if (targetType == TargetType.REPS) "reps" else "sec"
    return copy(
        targetValue = target,
        // An override is the program's own number, so weekly progression does not stack on it.
        progressionStep = 0,
        targetLabel = "$target $unit" + if (perSide) " each side" else "",
        defaultLoadKg = item.loadKg ?: defaultLoadKg
    )
}

/* -------------------------------------------------------------------- codec */

/**
 * Line-based text for [ProgramPlan]:
 * ```
 * days:k1,k2|k3            day rotations, `|`-separated (empty = every circuit every day)
 * c:key;name;slot;order;format;rest;work;rounds;minutes;items
 * w:…  s:…                 warm-up and stretch, same fields
 * items: exerciseId:sets:target:load, comma-separated (empty target/load = default)
 * ```
 * Names are URL-encoded so `;`, `,` and newlines in them are safe.
 */
object PlanCodec {

    fun encode(plan: ProgramPlan): String = buildList {
        add("days:" + plan.days.joinToString("|") { it.joinToString(",") })
        plan.circuits.forEach { add("c:" + encodeCircuit(it)) }
        add("w:" + encodeCircuit(plan.warmUp))
        add("s:" + encodeCircuit(plan.stretch))
    }.joinToString("\n")

    /** Null for blank or unreadable text, so callers fall back to a known plan. */
    fun decode(text: String): ProgramPlan? {
        if (text.isBlank()) return null
        return runCatching {
            var days = emptyList<List<String>>()
            val circuits = mutableListOf<CircuitPlan>()
            var warmUp = CircuitPlan(ProgramPlan.WARMUP_KEY, "Warm-up")
            var stretch = CircuitPlan(ProgramPlan.STRETCH_KEY, "Stretch")
            text.lines().filter { it.isNotBlank() }.forEach { line ->
                val tag = line.substringBefore(':')
                val body = line.substringAfter(':')
                when (tag) {
                    "days" -> days = if (body.isEmpty()) emptyList()
                    else body.split("|").map { d -> d.split(",").filter { it.isNotEmpty() } }
                    "c" -> circuits += decodeCircuit(body)
                    "w" -> warmUp = decodeCircuit(body)
                    "s" -> stretch = decodeCircuit(body)
                }
            }
            ProgramPlan(circuits, days, warmUp, stretch)
        }.getOrNull()
    }

    private fun encodeCircuit(c: CircuitPlan): String = listOf(
        c.key,
        URLEncoder.encode(c.name, "UTF-8"),
        c.slot.orEmpty(),
        c.order.name,
        c.format.name,
        c.restSeconds.toString(),
        c.workSeconds.toString(),
        c.rounds.toString(),
        c.minutes.toString(),
        c.items.joinToString(",") { i ->
            "${i.exerciseId}:${i.sets}:${i.target ?: ""}:${i.loadKg ?: ""}"
        }
    ).joinToString(";")

    private fun decodeCircuit(body: String): CircuitPlan {
        val f = body.split(";")
        return CircuitPlan(
            key = f[0],
            name = URLDecoder.decode(f[1], "UTF-8"),
            slot = f[2].ifEmpty { null },
            order = CircuitOrder.valueOf(f[3]),
            format = CircuitFormat.valueOf(f[4]),
            restSeconds = f[5].toInt(),
            workSeconds = f[6].toInt(),
            rounds = f[7].toInt(),
            minutes = f[8].toInt(),
            items = f.getOrElse(9) { "" }.split(",").filter { it.isNotEmpty() }.map { i ->
                val p = i.split(":")
                CircuitItem(
                    exerciseId = p[0],
                    sets = p.getOrElse(1) { "1" }.toInt(),
                    target = p.getOrNull(2)?.toIntOrNull(),
                    loadKg = p.getOrNull(3)?.toDoubleOrNull()
                )
            }
        )
    }
}

/* ---------------------------------------------------------------- validation */

object PlanValidation {

    const val MAX_SETS = 20

    /** Empty list means the plan is saveable. Messages are user-facing. */
    fun validate(plan: ProgramPlan): List<String> {
        val errors = mutableListOf<String>()
        if (plan.circuits.isEmpty()) errors += "Add at least one circuit."
        plan.circuits.forEach { c ->
            val label = c.name.ifBlank { "A circuit" }
            if (c.name.isBlank()) errors += "Every circuit needs a name."
            if (c.slot == null && c.items.isEmpty()) errors += "$label has no exercises."
            if (c.items.any { it.sets !in 1..MAX_SETS }) errors += "$label: sets must be 1 to $MAX_SETS."
            if (c.items.any { it.target != null && it.target !in 1..CatalogValidation.MAX_TARGET_VALUE }) {
                errors += "$label: targets must be 1 to ${CatalogValidation.MAX_TARGET_VALUE}."
            }
            if (c.items.map { it.exerciseId }.distinct().size != c.items.size) {
                errors += "$label lists an exercise twice — use sets instead."
            }
            when (c.format) {
                CircuitFormat.EMOM -> if (c.rounds < 1) errors += "$label: EMOM needs at least 1 minute."
                CircuitFormat.AMRAP -> if (c.minutes < 1) errors += "$label: AMRAP needs at least 1 minute."
                CircuitFormat.INTERVAL -> if (c.workSeconds < 1 || c.rounds < 1) {
                    errors += "$label: intervals need a work time and at least 1 round."
                }
                else -> Unit
            }
        }
        val keys = plan.circuits.map { it.key }.toSet()
        if (plan.days.any { day -> day.any { it !in keys } }) {
            errors += "A day runs a circuit that no longer exists."
        }
        return errors
    }
}
