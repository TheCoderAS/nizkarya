package com.nizkarya.app.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

/**
 * Voice quick-add: launches the system speech recognizer and hands the
 * transcript to the caller (which feeds it through the quick-add parser).
 * Uses the platform recognizer — no extra dependencies or API keys.
 */
@Composable
fun VoiceInputButton(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) onResult(spoken)
        }
    }
    IconButton(
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Say a task — e.g. Gym tomorrow at 6pm"
                )
            }
            try {
                launcher.launch(intent)
            } catch (e: Exception) {
                notify(scope, snackbar, "Voice input isn't available on this device.")
            }
        }
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Add task by voice",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
