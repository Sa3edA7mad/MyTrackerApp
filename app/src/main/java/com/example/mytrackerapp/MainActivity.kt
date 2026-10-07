package com.example.mytrackerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.mytrackerapp.data.prefs.ThemeMode
import com.example.mytrackerapp.ui.AppRoot
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.paletteFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsStore = (application as TrackerApplication).container.settings
        setContent {
            // null until DataStore's first read; fall back to the device mode meanwhile.
            val settings by settingsStore.settings.collectAsState(initial = null)
            val palette = paletteFor(settings?.themeMode ?: ThemeMode.SYSTEM, isSystemInDarkTheme())
            LaunchedEffect(palette.isLight) {
                val clear = android.graphics.Color.TRANSPARENT
                val style = if (palette.isLight) SystemBarStyle.light(clear, clear) else SystemBarStyle.dark(clear)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            MyTrackerAppTheme(palette = palette) {
                AppRoot()
            }
        }
    }
}
