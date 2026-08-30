package com.nizkarya.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.QuickAddParser
import com.nizkarya.app.widget.WidgetRefresh
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Speak a task straight onto the home screen.
 *
 * This activity has no UI of its own: it opens the system recogniser, feeds
 * the transcript through the same parser the in-app mic uses, writes the task
 * and finishes. Going through the app instead would mean a cold start, a
 * splash screen and a dashboard, all to capture one sentence.
 */
class VoiceAddActivity : ComponentActivity() {

    private lateinit var listener: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        listener = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val spoken = if (result.resultCode == RESULT_OK) {
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
            } else {
                null
            }
            if (spoken.isNullOrBlank()) finish() else save(spoken)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a task, like Gym tomorrow at 6pm")
        }
        try {
            listener.launch(intent)
        } catch (e: Exception) {
            toast("Voice input isn't available on this device.")
            finish()
        }
    }

    private fun save(spoken: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            toast("Sign in to add tasks.")
            finish()
            return
        }
        val parsed = QuickAddParser.parse(spoken)
        if (parsed.title.isBlank()) {
            toast("Didn't catch a task in that.")
            finish()
            return
        }
        val zone = ZoneId.systemDefault()
        val scheduled: Timestamp? = when {
            parsed.date != null -> Timestamp(
                Date.from(
                    parsed.date.atTime(parsed.time ?: LocalTime.of(9, 0))
                        .atZone(zone).toInstant()
                )
            )
            parsed.time != null -> Timestamp(
                Date.from(LocalDate.now(zone).atTime(parsed.time).atZone(zone).toInstant())
            )
            else -> null
        }
        lifecycleScope.launch {
            val saved = runCatching {
                TodoRepo.add(
                    uid, parsed.title, scheduled, parsed.priority,
                    parsed.tags, emptyList(), "", null, emptyList()
                )
            }.isSuccess
            toast(if (saved) "Added “${parsed.title}”" else "Couldn't add that")
            WidgetRefresh.request(applicationContext)
            finish()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
