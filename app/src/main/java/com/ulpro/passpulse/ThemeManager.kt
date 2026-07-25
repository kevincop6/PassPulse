package com.ulpro.passpulse

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

object ThemeManager {
    private const val THEME_KEY = "theme_mode"

    fun apply(context: Context) {
        val mode = PreferenceManager.getDefaultSharedPreferences(context).getString(THEME_KEY, "system")
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode))
    }

    fun apply(context: Context, mode: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(THEME_KEY, mode).apply()
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode))
    }

    private fun toNightMode(mode: String?) = when (mode) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
