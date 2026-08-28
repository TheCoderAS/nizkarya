package com.nizkarya.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Android drops every pending alarm on reboot, and again when the app is
 * replaced by an update. Both used to leave reminders silently dead until the
 * app happened to be opened. This rebuilds them.
 *
 * The rebuild needs a Firestore read, which is far too slow for onReceive, so
 * it hands off to a worker and returns immediately.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WorkManager.getInstance(context.applicationContext).enqueue(
                    OneTimeWorkRequestBuilder<ReminderSyncWorker>().build()
                )
                ReminderScheduler.enqueuePeriodicSync(context.applicationContext)
            }
        }
    }
}
