package com.nizkarya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.nizkarya.app.ui.NizKaryaApp
import com.nizkarya.app.ui.theme.AppSettings
import com.nizkarya.app.ui.theme.NizKaryaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        enableEdgeToEdge()
        setContent {
            val dark = when (AppSettings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            NizKaryaTheme(
                darkTheme = dark,
                dynamicColor = AppSettings.dynamicColor
            ) {
                NizKaryaApp()
            }
        }
    }
}
