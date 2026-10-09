package com.example.mytrackerapp.repo

import com.example.mytrackerapp.data.db.CircuitCount
import com.example.mytrackerapp.data.db.CircuitResultDao
import com.example.mytrackerapp.data.db.CompletionDao
import com.example.mytrackerapp.data.db.CycleDao
import com.example.mytrackerapp.data.db.DayDao
import com.example.mytrackerapp.data.db.ExerciseDao
import com.example.mytrackerapp.data.entity.CircuitResultEntity
import com.example.mytrackerapp.data.entity.CompletionEntity
import com.example.mytrackerapp.data.entity.CycleEntity
import com.example.mytrackerapp.data.entity.DayEntity
import com.example.mytrackerapp.data.entity.ExerciseEntity
import com.example.mytrackerapp.domain.CIRCUIT_STRETCH
import com.example.mytrackerapp.domain.CIRCUIT_WARMUP
import com.example.mytrackerapp.domain.CircuitPlan
import com.example.mytrackerapp.domain.Position
import com.example.mytrackerapp.domain.ProgramPlan
import com.example.mytrackerapp.domain.ProgramRules
import com.example.mytrackerapp.domain.parseStepKey
import com.example.mytrackerapp.domain.resolve
import com.example.mytrackerapp.domain.steps
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.CircuitProgress
import com.example.mytrackerapp.domain.model.CircuitView
import com.example.mytrackerapp.domain.model.CycleStats
import com.example.mytrackerapp.domain.model.CycleSummary
import com.example.mytrackerapp.domain.model.DayState
import com.example.mytrackerapp.domain.model.DaySummary
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.ExerciseDetail
import com.example.mytrackerapp.domain.model.ExerciseSlot
import com.example.mytrackerapp.domain.model.ExerciseTally
import com.example.mytrackerapp.domain.model.TargetType
import com.example.mytrackerapp.domain.model.TodayView
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.domain.model.WeekState
import com.example.mytrackerapp.domain.PerformanceSummary
import com.example.mytrackerapp.domain.SetLog
import com.example.mytrackerapp.domain.longestStreak
import com.example.mytrackerapp.domain.recentTallies
import com.example.mytrackerapp.domain.streakDays
import com.example.mytrackerapp.domain.summarise
import com.example.mytrackerapp.domain.trainingDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What can be logged against a single completed set. All optional — see INVARIANT 5. */
data class SetDetail(
    val reps: Int? = null,
    val loadKg: Double? = null,
    val bandLevel: String? = null,
    val holdSeconds: Int? = null,
    val rpe: Int? = null,
    val note: String? = null
)

/**
 * Days whose stretch routine is complete, for [ProgramRules.nextPosition]'s stretch-gate
 * check (INVARIANT 3). Pulled out once so every call site derives it the same way.
 */
private fun List<DayEntity>.stretchDonePositions(): Set<Position> =
    filter { it.stretchDoneAt != null }.map { Position(it.week, it.day) }.toSet()

fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    category = Category.valueOf(category),
    muscles = muscles,
    instructions = instructions,
    targetType = TargetType.valueOf(targetType),
    targetValue = targetValue,
    perSide = perSide,
    targetLabel = targetLabel,
    videoUrl = videoUrl,
    sortOrder = sortOrder,
    slot = runCatching { ExerciseSlot.valueOf(slot) }.getOrDefault(ExerciseSlot.PROGRAM),
    enabled = enabled,
    archivedAt = archivedAt,
    isCustom = isCustom,
    tracksReps = tracksReps,
    tracksLoad = tracksLoad,
    defaultLoadKg = defaultLoadKg,
    defaultBandLevel = defaultBandLevel,
    progressionStep = progressionStep,
    equipment = equipment,
    level = level,
    cue = cue,
    videoTitle = videoTitle,
    videoChannel = videoChannel
)

/** A cycle, the rules it was snapshotted under, and its frozen plan (INVARIANT 7). */
private data class Snapshot(val cycle: CycleEntity, val rules: ProgramRules, val plan: ProgramPlan?)

/**
 * The single funnel for all data access to one program — the program [rulesRepo] is bound
 * to (Home by default). No DAO is referenced outside this package.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackerRepository(
    private val exercises: ExerciseDao,
    private val cycles: CycleDao,
    private val days: DayDao,
    private val completions: CompletionDao,
    private val rulesRepo: RulesRepository,
    /** Optional so the repository tests that predate timed circuits need not pass it. */
    private val results: CircuitResultDao? = null
) {

    val programId: Long get() = rulesRepo.programId

    /**
     * INVARIANT 1: there is always exactly one active cycle per program.
     *
     * The database callback opens the first one, but this is called defensively at the
     * head of every read so no screen ever has to handle a null cycle. A freshly-created
     * cycle is snapshotted immediately (INVARIANT 7) so it never reads as ruleless.
     */
    suspend fun ensureActiveCycle(): Long = withContext(Dispatchers.IO) {
        cycles.getActive(programId)?.id ?: run {
            val id = cycles.insert(CycleEntity(startedAt = System.currentTimeMillis(), programId = programId))
            rulesRepo.snapshotRules(id)
            id
        }
    }

    private val activeCycle: Flow<CycleEntity> = cycles.observeActive(programId)
        .onEach { if (it == null) ensureActiveCycle() }
        .filterNotNull()

    /** The cycle paired with the rules it was snapshotted under (INVARIANT 7). */
    private val activeRules: Flow<Pair<CycleEntity, ProgramRules>> =
        activeCycle.flatMapLatest { cycle ->
            rulesRepo.observeRulesFor(cycle.id).map { cycle to it }
        }

    private val activeSnapshot: Flow<Snapshot> = activeRules.flatMapLatest { (cycle, rules) ->
        rulesRepo.observeCyclePlan(cycle.id).map { Snapshot(cycle, rules, it) }
    }

    /** The plan circuit a day's [circuit] slot runs, by name — "" for pre-program cycles. */
    private fun circuitName(plan: ProgramPlan?, rules: ProgramRules, day: Int, circuit: Int): String =
        plan?.circuits?.getOrNull(rules.planIndexFor(day, circuit))?.name.orEmpty()

    private fun circuitProgress(
        rules: ProgramRules,
        plan: ProgramPlan?,
        week: Int,
        day: Int,
        counts: Map<Int, Int>
    ): List<CircuitProgress> = (1..rules.circuitsFor(week, day)).map { index ->
        CircuitProgress(
            index = index,
            done = counts[index] ?: 0,
            total = rules.circuitSize(day, index),
            name = circuitName(plan, rules, day, index)
        )
    }

    /** INVARIANT 8: orphaned completions stay in the table but never enter a total. */
    private fun validCircuitCounts(
        cycleId: Long,
        rules: ProgramRules
    ): Flow<List<CircuitCount>> =
        completions.observeCircuitCounts(cycleId, rules.countRoutinesInTotals)
            .map { rows -> rows.filter { rules.isValidSlot(it.week, it.day, it.circuit) } }

    private fun dayCountsFrom(circuitCounts: List<CircuitCount>): Map<Position, Int> =
        circuitCounts.groupBy { Position(it.week, it.day) }
            .mapValues { (_, rows) -> rows.sumOf { it.done } }

    /* ------------------------------------------------------------------ today */

    fun observeToday(): Flow<UiState<TodayView>> = activeSnapshot.flatMapLatest { (cycle, rules, plan) ->
        combine(
            validCircuitCounts(cycle.id, rules),
            days.observeForCycle(cycle.id),
            exercises.observeAll()
        ) { circuitCounts, dayRows, catalog ->
            if (catalog.isEmpty()) return@combine UiState.Loading

            val doneByPosition = dayCountsFrom(circuitCounts)
            val closed = dayRows.filter { it.closedAt != null }
                .map { Position(it.week, it.day) }.toSet()

            val position = rules.nextPosition(doneByPosition, dayRows.stretchDonePositions(), closed)
                ?: return@combine UiState.Ready(TodayView.CycleComplete(rules.allPositions.size))

            val row = dayRows.firstOrNull { it.week == position.week && it.day == position.day }
            val counts = circuitCounts
                .filter { it.week == position.week && it.day == position.day }
                .associate { it.circuit to it.done }

            val circuits = circuitProgress(rules, plan, position.week, position.day, counts)
            val state = DayState(
                week = position.week,
                day = position.day,
                circuits = circuits,
                warmUpDone = row?.warmUpDoneAt != null,
                stretchDone = row?.stretchDoneAt != null,
                closed = row?.closedAt != null,
                exercisesPerCircuit = rules.exercisesPerCircuit,
                warmUpEnabled = rules.warmUpEnabled,
                stretchEnabled = rules.stretchEnabled,
                warmUpCount = rules.warmUpCount,
                stretchCount = rules.stretchCount,
                uniformCircuits = circuits.map { it.total }.distinct().size <= 1
            )
            UiState.Ready(TodayView.Active(state))
        }
    }

    /* ---------------------------------------------------------------- circuit */

    /**
     * @param circuit >= 1 for a program circuit, or CIRCUIT_WARMUP / CIRCUIT_STRETCH.
     */
    fun observeCircuit(week: Int, day: Int, circuit: Int): Flow<UiState<CircuitView>> =
        activeSnapshot.flatMapLatest { (cycle, rules, plan) ->
            combine(
                exercises.observeAll(),
                completions.observeDoneIn(cycle.id, week, day, circuit),
                validCircuitCounts(cycle.id, rules),
                days.observeForCycle(cycle.id),
                rulesRepo.observePlan()
            ) { catalog, doneKeys, circuitCounts, dayRows, draftPlan ->
                if (catalog.isEmpty()) return@combine UiState.Loading
                val domainCatalog = catalog.map { it.toDomain() }

                // INVARIANT 7: a program circuit shows exactly the composition its cycle was
                // snapshotted with — the same set its size counted — so archiving, disabling
                // or adding an exercise mid-cycle can't leave a circuit that never completes.
                // Routines read the live draft plan against the live catalog.
                val circuitPlan: CircuitPlan? = when (circuit) {
                    CIRCUIT_WARMUP -> draftPlan.resolve(domainCatalog).warmUp
                    CIRCUIT_STRETCH -> draftPlan.resolve(domainCatalog).stretch
                    else -> (plan ?: ProgramPlan.SLOT_DEFAULT.resolve(domainCatalog))
                        .circuits.getOrNull(rules.planIndexFor(day, circuit))
                }
                val steps = circuitPlan?.steps(domainCatalog.associateBy { it.id }).orEmpty()
                val done = doneKeys.map { it.exerciseId to it.setNumber }.toSet()

                // INVARIANT 4: a day past the current position is a read-only preview,
                // unless the rules have turned that lock off.
                val doneByPosition = dayCountsFrom(circuitCounts)
                val closed = dayRows.filter { it.closedAt != null }
                    .map { Position(it.week, it.day) }.toSet()
                val current = rules.nextPosition(doneByPosition, dayRows.stretchDonePositions(), closed)
                val editable = !rules.lockFutureDays ||
                    current == null ||
                    !rules.isAfter(Position(week, day), current)

                UiState.Ready(
                    CircuitView(
                        week = week,
                        day = day,
                        circuit = circuit,
                        exercises = steps.map { it.exercise.copy(id = it.key) },
                        doneIds = steps.filter { (it.exercise.id to it.set) in done }.map { it.key }.toSet(),
                        editable = editable,
                        setLabels = steps.filter { it.sets > 1 }
                            .associate { it.key to "Set ${it.set} of ${it.sets}" },
                        plan = circuitPlan.takeIf { circuit >= 1 && plan != null }
                    )
                )
            }
        }

    /* --------------------------------------------------------------- routines */

    /**
     * The day the user is currently on.
     *
     * `distinctUntilChanged` matters: without it every single checkbox tick re-emits the
     * same position and tears down whichever inner flow is collecting it.
     */
    private fun currentPositionFlow(): Flow<Position?> = activeRules.flatMapLatest { (cycle, rules) ->
        combine(
            validCircuitCounts(cycle.id, rules),
            days.observeForCycle(cycle.id)
        ) { circuitCounts, dayRows ->
            rules.nextPosition(
                dayCountsFrom(circuitCounts),
                dayRows.stretchDonePositions(),
                dayRows.filter { it.closedAt != null }
                    .map { Position(it.week, it.day) }.toSet()
            )
        }
    }.distinctUntilChanged()

    /**
     * One-shot equivalent of [validCircuitCounts]. `getDayCounts` cannot express the
     * per-week circuit bound that makes a completion an orphan (INVARIANT 8), so this
     * builds the same filtered view from the raw completion rows instead.
     */
    private suspend fun validDoneByPosition(cycleId: Long, rules: ProgramRules): Map<Position, Int> =
        completions.getAllForCycle(cycleId)
            .filter { rules.countsProgramSlot(it.circuit) && rules.isValidSlot(it.week, it.day, it.circuit) }
            .groupBy { Position(it.week, it.day) }
            .mapValues { (_, rows) -> rows.size }

    private suspend fun currentPosition(cycleId: Long): Position? {
        val rules = rulesRepo.rulesFor(cycleId)
        val dayRows = days.getForCycle(cycleId)
        return rules.nextPosition(
            validDoneByPosition(cycleId, rules),
            dayRows.stretchDonePositions(),
            dayRows.filter { it.closedAt != null }.map { Position(it.week, it.day) }.toSet()
        )
    }

    /**
     * Warm-up and stretch always belong to whatever day you are on, so unlike a circuit
     * they resolve their own position rather than taking one from the route.
     *
     * @param routineCircuit [CIRCUIT_WARMUP] or [CIRCUIT_STRETCH].
     */
    fun observeRoutine(routineCircuit: Int): Flow<UiState<CircuitView>> =
        currentPositionFlow().flatMapLatest { position ->
            if (position == null) {
                flowOf(UiState.Error("This cycle is finished."))
            } else {
                observeCircuit(position.week, position.day, routineCircuit)
            }
        }

    /** @param stepKey an exercise id, or a [com.example.mytrackerapp.domain.CircuitStep] key. */
    suspend fun setRoutineExerciseDone(
        routineCircuit: Int,
        stepKey: String,
        done: Boolean
    ) = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        val position = currentPosition(cycleId) ?: return@withContext
        setExerciseDone(position.week, position.day, routineCircuit, stepKey, done)
    }

    /** Sets the day-level flag. Deliberately writes no completions, so skipping works. */
    suspend fun markRoutineDone(routineCircuit: Int) = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        val position = currentPosition(cycleId) ?: return@withContext
        when (routineCircuit) {
            CIRCUIT_WARMUP -> markWarmUpDone(position.week, position.day)
            CIRCUIT_STRETCH -> markStretchDone(position.week, position.day)
            else -> Unit
        }
    }

    /* ---------------------------------------------------------------- program */

    fun observeProgram(): Flow<UiState<List<WeekState>>> = activeSnapshot.flatMapLatest { (cycle, rules, plan) ->
        combine(
            validCircuitCounts(cycle.id, rules),
            days.observeForCycle(cycle.id)
        ) { circuitCounts, dayRows ->
            val doneByPosition = dayCountsFrom(circuitCounts)
            val closed = dayRows.filter { it.closedAt != null }
                .map { Position(it.week, it.day) }.toSet()
            val current = rules.nextPosition(doneByPosition, dayRows.stretchDonePositions(), closed)
            val byDay = circuitCounts.groupBy { Position(it.week, it.day) }

            UiState.Ready(
                (1..rules.weeks).map { week ->
                    WeekState(
                        week = week,
                        circuitsPerDay = rules.circuitsFor(week, 1),
                        days = (1..rules.daysPerWeek).map { day ->
                            val p = Position(week, day)
                            val counts = byDay[p].orEmpty().associate { it.circuit to it.done }
                            DaySummary(
                                week = week,
                                day = day,
                                done = doneByPosition[p] ?: 0,
                                total = rules.exercisesPerDay(week, day),
                                closed = p in closed,
                                circuits = circuitProgress(rules, plan, week, day, counts)
                            )
                        },
                        isCurrent = current?.week == week,
                        exercisesPerCircuit = rules.exercisesPerCircuit
                    )
                }
            )
        }
    }

    /** The rules the running cycle was snapshotted under (INVARIANT 7). */
    fun observeActiveRules(): Flow<ProgramRules> = activeRules.map { it.second }

    /** The day the counter is on, for screens that need to gate editing (INVARIANT 4). */
    fun observeCurrentPosition(): Flow<Position?> = currentPositionFlow()

    /* ------------------------------------------------------------------ stats */

    /**
     * Stats for this program's cycle. The streak alone is combined across every program's
     * running cycle — a gym day keeps a home streak alive.
     */
    fun observeCycleStats(today: () -> LocalDate = { LocalDate.now() }): Flow<UiState<CycleStats>> =
        activeSnapshot.flatMapLatest { (cycle, rules, plan) ->
            combine(
                validCircuitCounts(cycle.id, rules),
                combine(
                    completions.observeCompletionTimes(cycle.id, rules.countRoutinesInTotals),
                    completions.observeActiveTrainingTimes()
                ) { mine, all -> mine to all },
                completions.observeTallies(cycle.id, rules.countRoutinesInTotals),
                days.observeForCycle(cycle.id),
                exercises.observeAll()
            ) { circuitCounts, (times, allTimes), tallies, dayRows, catalog ->
                if (catalog.isEmpty()) return@combine UiState.Loading

                val doneByPosition = dayCountsFrom(circuitCounts)
                val closed = dayRows.filter { it.closedAt != null }
                    .map { Position(it.week, it.day) }.toSet()
                val current = rules.nextPosition(doneByPosition, dayRows.stretchDonePositions(), closed)
                val nameById = catalog.associate { it.id to it.name }
                val byDay = circuitCounts.groupBy { Position(it.week, it.day) }

                val heat = rules.allPositions.map { p ->
                    val counts = byDay[p].orEmpty().associate { it.circuit to it.done }
                    DaySummary(
                        week = p.week,
                        day = p.day,
                        done = doneByPosition[p] ?: 0,
                        total = rules.exercisesPerDay(p.week, p.day),
                        closed = p in closed,
                        circuits = circuitProgress(rules, plan, p.week, p.day, counts)
                    )
                }

                val trainingDates = times.map { trainingDate(it, rules.dayRolloverHour) }.toSet()
                val anyProgramDates = (times + allTimes).map { trainingDate(it, rules.dayRolloverHour) }.toSet()

                UiState.Ready(
                    CycleStats(
                        streak = streakDays(anyProgramDates, today()),
                        exercisesDone = heat.sumOf { it.done },
                        exercisesTotal = rules.totalExercisesInCycle(),
                        circuitsDone = heat.sumOf { day -> day.circuits.count { it.isComplete } },
                        circuitsTotal = rules.totalCircuitsInCycle(),
                        daysTrained = trainingDates.size,
                        weeks = (1..rules.weeks).map { week ->
                            WeekState(
                                week = week,
                                circuitsPerDay = rules.circuitsFor(week, 1),
                                days = heat.filter { it.week == week },
                                isCurrent = current?.week == week,
                                exercisesPerCircuit = rules.exercisesPerCircuit
                            )
                        },
                        heat = heat,
                        mostDone = tallies.take(3).map {
                            ExerciseTally(it.exerciseId, nameById[it.exerciseId] ?: it.exerciseId, it.done)
                        }
                    )
                )
            }
        }

    /**
     * Summary of the active cycle, for the completion screen.
     *
     * Reports the *best* streak rather than the current one — at the end of a cycle the
     * live streak is often 1, which would undersell four weeks of work.
     */
    fun observeCycleSummary(today: () -> LocalDate = { LocalDate.now() }): Flow<UiState<CycleSummary>> =
        activeRules.flatMapLatest { (cycle, rules) ->
            combine(
                validCircuitCounts(cycle.id, rules),
                completions.observeCompletionTimes(cycle.id, rules.countRoutinesInTotals),
                days.observeForCycle(cycle.id)
            ) { circuitCounts, times, dayRows ->
                val trainingDates = times.map { trainingDate(it, rules.dayRolloverHour) }.toSet()
                val started = trainingDate(cycle.startedAt, rules.dayRolloverHour)
                val exercisesDone = circuitCounts.sumOf { it.done }
                UiState.Ready(
                    CycleSummary(
                        exercisesDone = exercisesDone,
                        exercisesTotal = rules.totalExercisesInCycle(),
                        circuitsDone = circuitCounts.count {
                            it.circuit >= 1 && it.done >= rules.circuitSize(it.day, it.circuit)
                        },
                        circuitsTotal = rules.totalCircuitsInCycle(),
                        daysTrained = trainingDates.size,
                        bestStreak = longestStreak(trainingDates),
                        elapsedDays = (ChronoUnit.DAYS.between(started, today()).toInt() + 1)
                            .coerceAtLeast(1),
                        daysClosedEarly = dayRows.count { it.closedAt != null },
                        weeks = rules.weeks
                    )
                )
            }
        }

    /** Active (not archived) catalog — what the Library browses. */
    fun observeCatalog(): Flow<List<Exercise>> =
        exercises.observeActive().map { all -> all.map { it.toDomain() } }

    /**
     * Catalog entry plus its history in every program's running cycle — an exercise can be
     * in several programs, and the detail screen is about the exercise.
     */
    fun observeExerciseDetail(id: String): Flow<UiState<ExerciseDetail>> =
        combine(
            exercises.observeAll(),
            completions.observeActiveTimesForExercise(id)
        ) { catalog, times ->
                if (catalog.isEmpty()) return@combine UiState.Loading
                val exercise = catalog.firstOrNull { it.id == id }?.toDomain()
                    ?: return@combine UiState.Error("That exercise is no longer in the program.")
                UiState.Ready(
                    ExerciseDetail(
                        exercise = exercise,
                        totalThisCycle = times.size,
                        recent = recentTallies(times)
                    )
                )
        }

    /** Rolling performance summary for the exercise detail screen's performance section. */
    fun observePerformance(exerciseId: String): Flow<PerformanceSummary> =
        activeRules.flatMapLatest { (_, rules) ->
            completions.observeActiveSetLogs(exerciseId).map { rows ->
                summarise(
                    rows.map { SetLog(it.completedAt, it.reps, it.loadKg, it.holdSeconds, it.rpe) },
                    rolloverHour = rules.dayRolloverHour
                )
            }
        }

    /** Writes reps/load/etc. onto an already-ticked completion, without changing its presence. */
    suspend fun updateSetDetail(
        week: Int,
        day: Int,
        circuit: Int,
        stepKey: String,
        detail: SetDetail
    ) = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        val (exerciseId, set) = parseStepKey(stepKey)
        completions.updateDetail(
            cycleId = cycleId,
            week = week,
            day = day,
            circuit = circuit,
            exerciseId = exerciseId,
            reps = detail.reps,
            loadKg = detail.loadKg,
            bandLevel = detail.bandLevel,
            holdSeconds = detail.holdSeconds,
            rpe = detail.rpe,
            note = detail.note,
            setNumber = set
        )
    }

    /** Most recently logged detail for an exercise in any program, to pre-fill the next set's sheet. */
    suspend fun lastDetailFor(stepKey: String): SetDetail? = withContext(Dispatchers.IO) {
        ensureActiveCycle()
        completions.getLastActiveDetail(parseStepKey(stepKey).first)?.let {
            SetDetail(
                reps = it.reps,
                loadKg = it.loadKg,
                bandLevel = it.bandLevel,
                holdSeconds = it.holdSeconds,
                rpe = it.rpe,
                note = it.note
            )
        }
    }

    /** For the rules editor: how many completions are currently outside the active rules. */
    suspend fun orphanedCompletionCount(): Int = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        val rules = rulesRepo.rulesFor(cycleId)
        completions.getAllForCycle(cycleId).count { !rules.isValidSlot(it.week, it.day, it.circuit) }
    }

    /* ------------------------------------------------------------------ writes */

    /** @param stepKey an exercise id, or `id#set` for one set of a multi-set exercise. */
    suspend fun setExerciseDone(
        week: Int,
        day: Int,
        circuit: Int,
        stepKey: String,
        done: Boolean,
        detail: SetDetail? = null
    ) = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        val (exerciseId, set) = parseStepKey(stepKey)
        ensureDayRow(cycleId, week, day)
        if (done) {
            completions.insert(
                CompletionEntity(
                    cycleId = cycleId,
                    week = week,
                    day = day,
                    circuit = circuit,
                    exerciseId = exerciseId,
                    completedAt = System.currentTimeMillis(),
                    reps = detail?.reps,
                    loadKg = detail?.loadKg,
                    bandLevel = detail?.bandLevel,
                    holdSeconds = detail?.holdSeconds,
                    rpe = detail?.rpe,
                    note = detail?.note,
                    setNumber = set
                )
            )
        } else {
            completions.delete(cycleId, week, day, circuit, exerciseId, set)
        }
    }

    /** The recorded AMRAP rounds / for-time seconds of one circuit slot, if any. */
    fun observeResult(week: Int, day: Int, circuit: Int): Flow<Int?> {
        val dao = results ?: return flowOf(null)
        return activeCycle.flatMapLatest { cycle ->
            dao.observe(cycle.id, week, day, circuit).map { it?.value }
        }
    }

    suspend fun saveResult(week: Int, day: Int, circuit: Int, value: Int) = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        results?.upsert(CircuitResultEntity(cycleId, week, day, circuit, value, System.currentTimeMillis()))
        Unit
    }

    suspend fun markWarmUpDone(week: Int, day: Int) = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        ensureDayRow(cycleId, week, day)
        days.markWarmUp(cycleId, week, day, System.currentTimeMillis())
    }

    suspend fun markStretchDone(week: Int, day: Int) = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        ensureDayRow(cycleId, week, day)
        days.markStretch(cycleId, week, day, System.currentTimeMillis())
    }

    /** INVARIANT 3: the only escape from a partly-done day. */
    suspend fun closeDayEarly(week: Int, day: Int) = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        ensureDayRow(cycleId, week, day)
        days.close(cycleId, week, day, System.currentTimeMillis())
    }

    /** INVARIANT 7: the new cycle is snapshotted from the current rules draft immediately. */
    suspend fun startNewCycle() = withContext(Dispatchers.IO) {
        cycles.getActive(programId)?.let { cycles.complete(it.id, System.currentTimeMillis()) }
        val id = cycles.insert(CycleEntity(startedAt = System.currentTimeMillis(), programId = programId))
        rulesRepo.snapshotRules(id)
        Unit
    }

    /** Wipes progress for the active cycle only. History of past cycles is untouched. */
    suspend fun resetActiveCycle() = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        completions.deleteForCycle(cycleId)
        days.deleteForCycle(cycleId)
        results?.deleteForCycle(cycleId)
    }

    private suspend fun ensureDayRow(cycleId: Long, week: Int, day: Int) {
        days.insertIgnore(DayEntity(cycleId = cycleId, week = week, day = day))
    }

    /* ----------------------------------------------------------------- export */

    /**
     * The only recovery path if auto-backup fails. Four weeks of training cannot be
     * regenerated, so this writes everything, not just the active cycle.
     */
    suspend fun exportJson(programs: JSONArray = JSONArray()): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        root.put("schemaVersion", 2)
        root.put("programs", programs)

        // Custom exercises exist nowhere else; the seeded catalog is regenerated by the app.
        val customArray = JSONArray()
        exercises.getAll().filter { it.isCustom }.forEach { e ->
            customArray.put(
                JSONObject()
                    .put("id", e.id).put("name", e.name).put("category", e.category)
                    .put("slot", e.slot).put("muscles", e.muscles).put("instructions", e.instructions)
                    .put("targetType", e.targetType).put("targetValue", e.targetValue)
                    .put("perSide", e.perSide).put("videoUrl", e.videoUrl)
                    .put("equipment", e.equipment).put("level", e.level).put("cue", e.cue)
                    .put("archived", e.archivedAt != null)
            )
        }
        root.put("customExercises", customArray)

        val cycleArray = JSONArray()
        cycles.getAll().forEach { cycle ->
            val c = JSONObject()
                .put("id", cycle.id)
                .put("startedAt", cycle.startedAt)
                .put("completedAt", cycle.completedAt ?: JSONObject.NULL)
                .put("isActive", cycle.isActive)
                .put("programId", cycle.programId)
                .put("plan", rulesRepo.cyclePlanText(cycle.id))

            val completionArray = JSONArray()
            completions.getAllForCycle(cycle.id).forEach { done ->
                completionArray.put(
                    JSONObject()
                        .put("week", done.week)
                        .put("day", done.day)
                        .put("circuit", done.circuit)
                        .put("exerciseId", done.exerciseId)
                        .put("setNumber", done.setNumber)
                        .put("reps", done.reps ?: JSONObject.NULL)
                        .put("loadKg", done.loadKg ?: JSONObject.NULL)
                        .put("bandLevel", done.bandLevel ?: JSONObject.NULL)
                        .put("holdSeconds", done.holdSeconds ?: JSONObject.NULL)
                        .put("rpe", done.rpe ?: JSONObject.NULL)
                        .put("note", done.note ?: JSONObject.NULL)
                        .put("completedAt", done.completedAt)
                )
            }
            c.put("completions", completionArray)
            val resultArray = JSONArray()
            results?.getAllForCycle(cycle.id)?.forEach { r ->
                resultArray.put(
                    JSONObject().put("week", r.week).put("day", r.day).put("circuit", r.circuit)
                        .put("value", r.value).put("recordedAt", r.recordedAt)
                )
            }
            c.put("circuitResults", resultArray)
            cycleArray.put(c)
        }
        root.put("cycles", cycleArray)
        root.toString(2)
    }
}
