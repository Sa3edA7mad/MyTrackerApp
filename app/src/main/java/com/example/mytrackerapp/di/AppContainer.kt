package com.example.mytrackerapp.di

import android.content.Context
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.prefs.SettingsStore
import com.example.mytrackerapp.repo.TrackerRepository

/**
 * Manual dependency container. No Hilt — this app has one database, one settings store
 * and one repository, and a hand-written container keeps the build free of a second
 * annotation processor.
 *
 * ViewModels reach this through TrackerApplication via their `Factory`.
 */
class AppContainer(context: Context) {

    val db: AppDatabase by lazy { AppDatabase.build(context) }

    val settings: SettingsStore by lazy { SettingsStore(context.applicationContext) }

    val repo: TrackerRepository by lazy {
        TrackerRepository(
            exercises = db.exerciseDao(),
            cycles = db.cycleDao(),
            days = db.dayDao(),
            completions = db.completionDao()
        )
    }
}
