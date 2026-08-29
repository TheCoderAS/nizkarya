package com.nizkarya.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Small, synchronous preference store for appearance choices. Backed by
 * SharedPreferences and exposed as Compose state so toggles apply instantly.
 */
object AppSettings {

    private const val PREFS = "nizkarya_prefs"
    private const val KEY_DYNAMIC = "dynamic_color"
    private const val KEY_THEME = "theme_mode" // system | light | dark

    // Off by default: NizKarya opens in its own brand look, and wallpaper
    // colours are an opt-in from Profile.
    var dynamicColor by mutableStateOf(false)
        private set

    var themeMode by mutableStateOf("system")
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false)
        themeMode = prefs.getString(KEY_THEME, "system") ?: "system"
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        dynamicColor = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun setThemeMode(context: Context, mode: String) {
        themeMode = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode).apply()
    }
}
