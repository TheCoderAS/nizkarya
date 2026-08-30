package com.nizkarya.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * The quick-add widget's plus.
 *
 * Glance can start an Activity by class or by component name, but the
 * overload that carries an Intent lives elsewhere in the library, so a widget
 * cannot easily hand the app an action to perform. This activity is that
 * action: it records the request, opens the app and finishes, never drawing
 * anything of its own.
 */
class NewTaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LaunchIntents.request()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
