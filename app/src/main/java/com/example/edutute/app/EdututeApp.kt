package com.example.edutute.app

import android.app.Application
import com.example.edutute.core.ui.ThemePreferenceManager

class EdututeApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ThemePreferenceManager(this).applySavedTheme()
        appContainer = AppContainer(this)
    }
}
