package com.nizkarya.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nizkarya.app.ui.NizKaryaApp
import com.nizkarya.app.ui.theme.NizKaryaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NizKaryaTheme {
                NizKaryaApp()
            }
        }
    }
}
