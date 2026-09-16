package com.example.mytrackerapp.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    /** Guided pager vs. flat checklist. Switching mid-circuit updates this default. */
    val guidedMode: Boolean = true,
    val haptics: Boolean = true,
    /**
     * Audio countdown cues. On by default because during a Dead Hang the phone is on
     * the floor and you are hanging from a bar — haptics are unreachable.
     */
    val soundCues: Boolean = true,
    val keepScreenOn: Boolean = true,
    val autoAdvanceTimer: Boolean = true
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val guidedMode = booleanPreferencesKey("guided_mode")
        val haptics = booleanPreferencesKey("haptics")
        val soundCues = booleanPreferencesKey("sound_cues")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val autoAdvanceTimer = booleanPreferencesKey("auto_advance_timer")
    }

    val settings: Flow<Settings> = context.settingsDataStore.data.map { p ->
        val defaults = Settings()
        Settings(
            guidedMode = p[Keys.guidedMode] ?: defaults.guidedMode,
            haptics = p[Keys.haptics] ?: defaults.haptics,
            soundCues = p[Keys.soundCues] ?: defaults.soundCues,
            keepScreenOn = p[Keys.keepScreenOn] ?: defaults.keepScreenOn,
            autoAdvanceTimer = p[Keys.autoAdvanceTimer] ?: defaults.autoAdvanceTimer
        )
    }

    suspend fun setGuidedMode(value: Boolean) = put(Keys.guidedMode, value)
    suspend fun setHaptics(value: Boolean) = put(Keys.haptics, value)
    suspend fun setSoundCues(value: Boolean) = put(Keys.soundCues, value)
    suspend fun setKeepScreenOn(value: Boolean) = put(Keys.keepScreenOn, value)
    suspend fun setAutoAdvanceTimer(value: Boolean) = put(Keys.autoAdvanceTimer, value)

    private suspend fun put(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsDataStore.edit { it[key] = value }
    }
}
