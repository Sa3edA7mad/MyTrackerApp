package com.example.mytrackerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mytrackerapp.ui.AppRoot
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyTrackerAppTheme {
                AppRoot()
            }
        }
    }
}
