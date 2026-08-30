package com.nizkarya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import com.nizkarya.app.ui.NizKaryaApp
import com.nizkarya.app.widget.WidgetRefresh
import com.nizkarya.app.ui.theme.AppSettings
import com.nizkarya.app.ui.theme.NizKaryaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the platform swaps the launch
        // theme for the real one instead of flashing a bare window.
        installSplashScreen()
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

    override fun onStop() {
        super.onStop()
        // Leaving the app is the moment the home screen is about to be looked
        // at, so it is the right moment to redraw what sits on it.
        WidgetRefresh.request(applicationContext)
    }
}
