package com.example.mytrackerapp.repo

import com.example.mytrackerapp.data.db.CompletionDao
import com.example.mytrackerapp.data.db.CycleDao
import com.example.mytrackerapp.data.db.DayDao
import com.example.mytrackerapp.data.db.ExerciseDao
import com.example.mytrackerapp.data.entity.CompletionEntity
import com.example.mytrackerapp.data.entity.CycleEntity
import com.example.mytrackerapp.data.entity.DayEntity
import com.example.mytrackerapp.data.entity.ExerciseEntity
import com.example.mytrackerapp.domain.ALL_POSITIONS
import com.example.mytrackerapp.domain.CIRCUIT_STRETCH
import com.example.mytrackerapp.domain.CIRCUIT_WARMUP
import com.example.mytrackerapp.domain.EXERCISES_PER_CIRCUIT
import com.example.mytrackerapp.domain.DAYS_PER_WEEK
import com.example.mytrackerapp.domain.Position
import com.example.mytrackerapp.domain.WEEKS
import com.example.mytrackerapp.domain.circuitsForWeek
import com.example.mytrackerapp.domain.exercisesPerDay
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.CircuitProgress
import com.example.mytrackerapp.domain.model.CircuitView
import com.example.mytrackerapp.domain.model.CycleStats
import com.example.mytrackerapp.domain.model.CycleSummary
import com.example.mytrackerapp.domain.model.DayState
import com.example.mytrackerapp.domain.model.DaySummary
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.domain.model.ExerciseDetail
import com.example.mytrackerapp.domain.model.ExerciseTally
import com.example.mytrackerapp.domain.model.TargetType
import com.example.mytrackerapp.domain.model.TodayView
import com.example.mytrackerapp.domain.model.UiState
import com.example.mytrackerapp.domain.model.WeekState
import com.example.mytrackerapp.domain.longestStreak
import com.example.mytrackerapp.domain.nextPosition
import com.example.mytrackerapp.domain.recentTallies
import com.example.mytrackerapp.domain.streakDays
import com.example.mytrackerapp.domain.totalCircuitsInCycle
import com.example.mytrackerapp.domain.totalExercisesInCycle
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
    sortOrder = sortOrder
)

/**
 * The single funnel for all data access. No DAO is referenced outside this package.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackerRepository(
    private val exercises: ExerciseDao,
    private val cycles: CycleDao,
    private val days: DayDao,
    private val completions: CompletionDao
) {

    /**
     * INVARIANT 1: there is always exactly one active cycle.
     *
     * The database callback opens the first one, but this is called defensively at the
     * head of every read so no screen ever has to handle a null cycle.
     */
    suspend fun ensureActiveCycle(): Long = withContext(Dispatchers.IO) {
        cycles.getActive()?.id
            ?: cycles.insert(CycleEntity(startedAt = System.currentTimeMillis()))
    }

    private val activeCycle: Flow<CycleEntity> = cycles.observeActive()
        .onEach { if (it == null) ensureActiveCycle() }
        .filterNotNull()

    /* ------------------------------------------------------------------ today */

    fun observeToday(): Flow<UiState<TodayView>> = activeCycle.flatMapLatest { cycle ->
        combine(
            completions.observeCircuitCounts(cycle.id),
            completions.observeDayCounts(cycle.id),
            days.observeForCycle(cycle.id),
            exercises.observeAll()
        ) { circuitCounts, dayCounts, dayRows, catalog ->
            if (catalog.isEmpty()) return@combine UiState.Loading

            val doneByPosition = dayCounts.associate { Position(it.week, it.day) to it.done }
            val closed = dayRows.filter { it.closedAt != null }
                .map { Position(it.week, it.day) }.toSet()

            val position = nextPosition(doneByPosition, closed)
                ?: return@combine UiState.Ready(TodayView.CycleComplete)

            val row = dayRows.firstOrNull { it.week == position.week && it.day == position.day }
            val counts = circuitCounts
                .filter { it.week == position.week && it.day == position.day }
                .associate { it.circuit to it.done }

            val state = DayState(
                week = position.week,
                day = position.day,
                circuits = (1..circuitsForWeek(position.week)).map { index ->
                    CircuitProgress(index = index, done = counts[index] ?: 0)
                },
                warmUpDone = row?.warmUpDoneAt != null,
                stretchDone = row?.stretchDoneAt != null,
                closed = row?.closedAt != null
            )
            UiState.Ready(TodayView.Active(state))
        }
    }

    /* ---------------------------------------------------------------- circuit */

    /**
     * @param circuit >= 1 for a program circuit, or CIRCUIT_WARMUP / CIRCUIT_STRETCH.
     */
    fun observeCircuit(week: Int, day: Int, circuit: Int): Flow<UiState<CircuitView>> =
        activeCycle.flatMapLatest { cycle ->
            combine(
                exercises.observeAll(),
                completions.observeExerciseIdsIn(cycle.id, week, day, circuit),
                completions.observeDayCounts(cycle.id),
                days.observeForCycle(cycle.id)
            ) { catalog, doneIds, dayCounts, dayRows ->
                if (catalog.isEmpty()) return@combine UiState.Loading

                val wanted = when (circuit) {
                    CIRCUIT_WARMUP -> listOf(Category.WARMUP)
                    CIRCUIT_STRETCH -> listOf(Category.STRETCH)
                    else -> listOf(Category.BODYWEIGHT, Category.BAND)
                }
                val list = catalog.map { it.toDomain() }
                    .filter { it.category in wanted }
                    .sortedBy { it.sortOrder }

                // INVARIANT 4: a day past the current position is a read-only preview.
                val doneByPosition = dayCounts.associate { Position(it.week, it.day) to it.done }
                val closed = dayRows.filter { it.closedAt != null }
                    .map { Position(it.week, it.day) }.toSet()
                val current = nextPosition(doneByPosition, closed)
                val editable = current == null || !isAfter(Position(week, day), current)

                UiState.Ready(
                    CircuitView(
                        week = week,
                        day = day,
                        circuit = circuit,
                        exercises = list,
                        doneIds = doneIds.toSet(),
                        editable = editable
                    )
                )
            }
        }

    private fun isAfter(candidate: Position, current: Position): Boolean =
        ALL_POSITIONS.indexOf(candidate) > ALL_POSITIONS.indexOf(current)

    /* --------------------------------------------------------------- routines */

    /**
     * The day the user is currently on.
     *
     * `distinctUntilChanged` matters: without it every single checkbox tick re-emits the
     * same position and tears down whichever inner flow is collecting it.
     */
    private fun currentPositionFlow(): Flow<Position?> = activeCycle.flatMapLatest { cycle ->
        combine(
            completions.observeDayCounts(cycle.id),
            days.observeForCycle(cycle.id)
        ) { dayCounts, dayRows ->
            nextPosition(
                dayCounts.associate { Position(it.week, it.day) to it.done },
                dayRows.filter { it.closedAt != null }
                    .map { Position(it.week, it.day) }.toSet()
            )
        }
    }.distinctUntilChanged()

    private suspend fun currentPosition(cycleId: Long): Position? = nextPosition(
        completions.getDayCounts(cycleId).associate { Position(it.week, it.day) to it.done },
        days.getForCycle(cycleId).filter { it.closedAt != null }
            .map { Position(it.week, it.day) }.toSet()
    )

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

    suspend fun setRoutineExerciseDone(
        routineCircuit: Int,
        exerciseId: String,
        done: Boolean
    ) = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        val position = currentPosition(cycleId) ?: return@withContext
        setExerciseDone(position.week, position.day, routineCircuit, exerciseId, done)
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

    fun observeProgram(): Flow<UiState<List<WeekState>>> = activeCycle.flatMapLatest { cycle ->
        combine(
            completions.observeDayCounts(cycle.id),
            completions.observeCircuitCounts(cycle.id),
            days.observeForCycle(cycle.id)
        ) { dayCounts, circuitCounts, dayRows ->
            val doneByPosition = dayCounts.associate { Position(it.week, it.day) to it.done }
            val closed = dayRows.filter { it.closedAt != null }
                .map { Position(it.week, it.day) }.toSet()
            val current = nextPosition(doneByPosition, closed)
            val byDay = circuitCounts.groupBy { Position(it.week, it.day) }

            UiState.Ready(
                (1..WEEKS).map { week ->
                    WeekState(
                        week = week,
                        circuitsPerDay = circuitsForWeek(week),
                        days = (1..DAYS_PER_WEEK).map { day ->
                            val p = Position(week, day)
                            val counts = byDay[p].orEmpty().associate { it.circuit to it.done }
                            DaySummary(
                                week = week,
                                day = day,
                                done = doneByPosition[p] ?: 0,
                                total = exercisesPerDay(week),
                                closed = p in closed,
                                circuits = (1..circuitsForWeek(week)).map { index ->
                                    CircuitProgress(index = index, done = counts[index] ?: 0)
                                }
                            )
                        },
                        isCurrent = current?.week == week
                    )
                }
            )
        }
    }

    /** The day the counter is on, for screens that need to gate editing (INVARIANT 4). */
    fun observeCurrentPosition(): Flow<Position?> = currentPositionFlow()

    /* ------------------------------------------------------------------ stats */

    fun observeCycleStats(today: () -> LocalDate = { LocalDate.now() }): Flow<UiState<CycleStats>> =
        activeCycle.flatMapLatest { cycle ->
            combine(
                completions.observeDayCounts(cycle.id),
                completions.observeCompletionTimes(cycle.id),
                completions.observeTallies(cycle.id),
                days.observeForCycle(cycle.id),
                exercises.observeAll()
            ) { dayCounts, times, tallies, dayRows, catalog ->
                if (catalog.isEmpty()) return@combine UiState.Loading

                val doneByPosition = dayCounts.associate { Position(it.week, it.day) to it.done }
                val closed = dayRows.filter { it.closedAt != null }
                    .map { Position(it.week, it.day) }.toSet()
                val current = nextPosition(doneByPosition, closed)
                val nameById = catalog.associate { it.id to it.name }

                val heat = ALL_POSITIONS.map { p ->
                    DaySummary(
                        week = p.week,
                        day = p.day,
                        done = doneByPosition[p] ?: 0,
                        total = exercisesPerDay(p.week),
                        closed = p in closed
                    )
                }

                val trainingDates = times.map { trainingDate(it) }.toSet()

                UiState.Ready(
                    CycleStats(
                        streak = streakDays(trainingDates, today()),
                        exercisesDone = times.size,
                        exercisesTotal = totalExercisesInCycle(),
                        circuitsDone = heat.sumOf { it.done / com.example.mytrackerapp.domain.EXERCISES_PER_CIRCUIT },
                        circuitsTotal = totalCircuitsInCycle(),
                        daysTrained = trainingDates.size,
                        weeks = (1..WEEKS).map { week ->
                            WeekState(
                                week = week,
                                circuitsPerDay = circuitsForWeek(week),
                                days = heat.filter { it.week == week },
                                isCurrent = current?.week == week
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
        activeCycle.flatMapLatest { cycle ->
            combine(
                completions.observeCompletionTimes(cycle.id),
                days.observeForCycle(cycle.id)
            ) { times, dayRows ->
                val trainingDates = times.map { trainingDate(it) }.toSet()
                val started = trainingDate(cycle.startedAt)
                UiState.Ready(
                    CycleSummary(
                        exercisesDone = times.size,
                        exercisesTotal = totalExercisesInCycle(),
                        circuitsDone = times.size / EXERCISES_PER_CIRCUIT,
                        circuitsTotal = totalCircuitsInCycle(),
                        daysTrained = trainingDates.size,
                        bestStreak = longestStreak(trainingDates),
                        elapsedDays = (ChronoUnit.DAYS.between(started, today()).toInt() + 1)
                            .coerceAtLeast(1),
                        daysClosedEarly = dayRows.count { it.closedAt != null }
                    )
                )
            }
        }

    fun observeCatalog(): Flow<List<Exercise>> =
        exercises.observeAll().map { all -> all.map { it.toDomain() } }

    /** Catalog entry plus this cycle's history, for the exercise detail screen. */
    fun observeExerciseDetail(id: String): Flow<UiState<ExerciseDetail>> =
        activeCycle.flatMapLatest { cycle ->
            combine(
                exercises.observeAll(),
                completions.observeAllTimesForExercise(cycle.id, id)
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
        }

    /* ------------------------------------------------------------------ writes */

    suspend fun setExerciseDone(
        week: Int,
        day: Int,
        circuit: Int,
        exerciseId: String,
        done: Boolean
    ) = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        ensureDayRow(cycleId, week, day)
        if (done) {
            completions.insert(
                CompletionEntity(
                    cycleId = cycleId,
                    week = week,
                    day = day,
                    circuit = circuit,
                    exerciseId = exerciseId,
                    completedAt = System.currentTimeMillis()
                )
            )
        } else {
            completions.delete(cycleId, week, day, circuit, exerciseId)
        }
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

    suspend fun startNewCycle() = withContext(Dispatchers.IO) {
        cycles.getActive()?.let { cycles.complete(it.id, System.currentTimeMillis()) }
        cycles.insert(CycleEntity(startedAt = System.currentTimeMillis()))
        Unit
    }

    /** Wipes progress for the active cycle only. History of past cycles is untouched. */
    suspend fun resetActiveCycle() = withContext(Dispatchers.IO) {
        val cycleId = ensureActiveCycle()
        completions.deleteForCycle(cycleId)
        days.deleteForCycle(cycleId)
    }

    private suspend fun ensureDayRow(cycleId: Long, week: Int, day: Int) {
        days.insertIgnore(DayEntity(cycleId = cycleId, week = week, day = day))
    }

    /* ----------------------------------------------------------------- export */

    /**
     * The only recovery path if auto-backup fails. Four weeks of training cannot be
     * regenerated, so this writes everything, not just the active cycle.
     */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        root.put("schemaVersion", 1)

        val cycleArray = JSONArray()
        cycles.getAll().forEach { cycle ->
            val c = JSONObject()
                .put("id", cycle.id)
                .put("startedAt", cycle.startedAt)
                .put("completedAt", cycle.completedAt ?: JSONObject.NULL)
                .put("isActive", cycle.isActive)

            val completionArray = JSONArray()
            completions.getAllForCycle(cycle.id).forEach { done ->
                completionArray.put(
                    JSONObject()
                        .put("week", done.week)
                        .put("day", done.day)
                        .put("circuit", done.circuit)
                        .put("exerciseId", done.exerciseId)
                        .put("completedAt", done.completedAt)
                )
            }
            c.put("completions", completionArray)
            cycleArray.put(c)
        }
        root.put("cycles", cycleArray)
        root.toString(2)
    }
}
