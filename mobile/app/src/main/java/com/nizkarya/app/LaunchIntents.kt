package com.nizkarya.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Requests that arrive from outside the app: a widget tap, a Quick Settings
 * tile, a launcher shortcut.
 *
 * An Intent reaches an Activity, not a composable, so the Activity records
 * what was asked for here and the screen that can honour it picks it up and
 * clears the flag. Anything set before the UI exists is still waiting when it
 * appears, which is the case that matters, since a cold start is exactly what
 * a home screen tap causes.
 */
object LaunchIntents {

    var pendingNewTask by mutableStateOf(false)
        private set

    fun request() {
        pendingNewTask = true
    }

    fun consume() {
        pendingNewTask = false
    }
}
