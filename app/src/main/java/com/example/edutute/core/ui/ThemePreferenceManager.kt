package com.example.edutute.core.ui

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

class ThemePreferenceManager(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(currentNightMode())
    }

    fun isDarkModeEnabled(): Boolean =
        when (currentNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val nightMode = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }

    fun setDarkModeEnabled(enabled: Boolean) {
        preferences.edit()
            .putInt(KEY_THEME_MODE, if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            .apply()
        AppCompatDelegate.setDefaultNightMode(currentNightMode())
    }

    private fun currentNightMode(): Int =
        preferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    private companion object {
        const val PREFERENCES_NAME = "edutute_preferences"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
