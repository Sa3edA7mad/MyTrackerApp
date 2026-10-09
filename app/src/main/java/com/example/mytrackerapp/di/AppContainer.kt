package com.example.mytrackerapp.di

import android.content.Context
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.prefs.SettingsStore
import com.example.mytrackerapp.repo.CatalogRepository
import com.example.mytrackerapp.repo.MeasurementRepository
import com.example.mytrackerapp.repo.ProgramRepository
import com.example.mytrackerapp.repo.RulesRepository
import com.example.mytrackerapp.repo.TrackerRepository

/**
 * Manual dependency container. No Hilt — this app has one database, one settings store
 * and a small number of repositories, and a hand-written container keeps the build free
 * of a second annotation processor.
 *
 * ViewModels reach this through TrackerApplication via their `Factory`.
 */
class AppContainer(context: Context) {

    val db: AppDatabase by lazy { AppDatabase.build(context) }

    val settings: SettingsStore by lazy { SettingsStore(context.applicationContext) }

    /** Home's rules (program 1) — also the source of the app-wide display units. */
    val rules: RulesRepository by lazy { rulesFor(1) }

    val programs: ProgramRepository by lazy { ProgramRepository(db.programDao(), db.rulesDao()) }

    private val rulesByProgram = mutableMapOf<Long, RulesRepository>()
    private val repoByProgram = mutableMapOf<Long, TrackerRepository>()

    /** One rules repository per program, created on first use. */
    @Synchronized
    fun rulesFor(programId: Long): RulesRepository = rulesByProgram.getOrPut(programId) {
        RulesRepository(
            dao = db.rulesDao(),
            exercises = db.exerciseDao(),
            days = db.dayDao(),
            completions = db.completionDao(),
            programId = programId
        )
    }

    /** One tracker repository per program, created on first use. */
    @Synchronized
    fun repoFor(programId: Long): TrackerRepository = repoByProgram.getOrPut(programId) {
        TrackerRepository(
            exercises = db.exerciseDao(),
            cycles = db.cycleDao(),
            days = db.dayDao(),
            completions = db.completionDao(),
            rulesRepo = rulesFor(programId),
            results = db.resultDao()
        )
    }

    val catalog: CatalogRepository by lazy { CatalogRepository(db.exerciseDao()) }

    val measurements: MeasurementRepository by lazy {
        MeasurementRepository(
            metrics = db.metricDao(),
            measurements = db.measurementDao(),
            unitPrefs = { rules.observeUnits() }
        )
    }

    /** Home's repository (program 1), for program-agnostic reads: catalog, exercise history, export. */
    val repo: TrackerRepository by lazy { repoFor(1) }
}
