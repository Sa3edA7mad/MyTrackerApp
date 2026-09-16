package com.example.mytrackerapp

import android.app.Application
import com.example.mytrackerapp.di.AppContainer

class TrackerApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
