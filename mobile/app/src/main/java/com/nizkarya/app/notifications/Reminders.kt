package com.nizkarya.app.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.nizkarya.app.MainActivity
import com.nizkarya.app.R
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.logic.HabitLogic
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Brand violet, used to tint the notification's small icon. */
private const val BRAND_VIOLET = 0xFF8B7CF6.toInt()

/** Give a Firestore write from a receiver a moment to reach the local queue. */
private const val ACTION_WRITE_TIMEOUT_MS = 8_000L

const val EXTRA_HABIT_ID = "habitId"
const val EXTRA_DATE_KEY = "dateKey"
const val EXTRA_TITLE = "title"
const val EXTRA_BODY = "body"

const val ACTION_DISMISS = "com.nizkarya.app.DISMISS"
const val ACTION_DONE = "com.nizkarya.app.MARK_DONE"
const val ACTION_SNOOZE = "com.nizkarya.app.SNOOZE"

/**
 * On-device reminders, with no server or FCM involved. Habit reminders for today
 * are (re)scheduled whenever the habit list changes while the app is open;
 * exact alarms are used when permitted, with a windowed fallback.
 */
object Reminders {

    const val CHANNEL_ID = "reminders"

    /** How long "In an hour" pushes a reminder back. */
    private const val SNOOZE_MILLIS = 60L * 60L * 1000L

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    /** Stable per-habit id, so one habit never posts two competing reminders. */
    fun notificationId(habitId: String): Int = habitId.hashCode()

    private fun dailyRequestCode(habitId: String): Int = habitId.hashCode()

    /**
     * A snoozed alarm needs its own slot. Sharing the daily one would mean the
     * next pass of [scheduleHabitReminders] saw today's already-past reminder
     * time, decided it should not fire, and cancelled the snooze the moment the
     * app was opened.
     */
    private fun snoozeRequestCode(habitId: String): Int = habitId.hashCode() * 31 + 1

    private fun reminderIntent(
        context: Context,
        habitId: String,
        dateKey: String,
        title: String,
        body: String
    ) = Intent(context, ReminderReceiver::class.java)
        .putExtra(EXTRA_HABIT_ID, habitId)
        .putExtra(EXTRA_DATE_KEY, dateKey)
        .putExtra(EXTRA_TITLE, title)
        .putExtra(EXTRA_BODY, body)

    private fun setAlarm(context: Context, triggerAt: Long, pending: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            val canExact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pending
                )
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, pending
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, pending)
        }
    }

    fun scheduleHabitReminders(context: Context, habits: List<Habit>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        habits.forEach { habit ->
            val zone = HabitLogic.zoneOf(habit.timezone)
            val today = LocalDate.now(zone)
            val dateKey = today.toString()
            val body = "Time for this one. Keep your streak going."

            val pending = PendingIntent.getBroadcast(
                context,
                dailyRequestCode(habit.id),
                reminderIntent(context, habit.id, dateKey, habit.title, body),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val time = runCatching { LocalTime.parse(habit.reminderTime) }.getOrNull()
            val triggerAt = time?.let {
                today.atTime(it).atZone(zone).toInstant().toEpochMilli()
            }
            val shouldFire = habit.archivedAt == null &&
                triggerAt != null &&
                triggerAt > System.currentTimeMillis() &&
                HabitLogic.isScheduledOn(habit, today) &&
                dateKey !in habit.completionDates

            if (!shouldFire) {
                // Habit was completed, archived, or unscheduled, so drop any
                // alarm already set. This clears a pending snooze too, or
                // finishing a habit in the app would still leave an hour-later
                // reminder armed.
                alarmManager.cancel(pending)
                cancelSnooze(context, habit.id)
                return@forEach
            }

            setAlarm(context, triggerAt!!, pending)
        }
    }

    /** Re-arm one reminder an hour out, keeping the same habit day. */
    fun snooze(context: Context, habitId: String, dateKey: String, title: String, body: String) {
        val pending = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(habitId),
            reminderIntent(context, habitId, dateKey, title, body),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(context, System.currentTimeMillis() + SNOOZE_MILLIS, pending)
    }

    fun cancelSnooze(context: Context, habitId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(habitId),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    private fun action(
        context: Context,
        label: String,
        action: String,
        habitId: String,
        dateKey: String,
        title: String,
        body: String
    ): Notification.Action {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_HABIT_ID, habitId)
            .putExtra(EXTRA_DATE_KEY, dateKey)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_BODY, body)
        val pending = PendingIntent.getBroadcast(
            context,
            // Distinct per habit and per action, or the three would collide.
            (habitId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_notification),
            label,
            pending
        ).build()
    }

    fun post(context: Context, habitId: String, dateKey: String, title: String, body: String) {
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            habitId.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_VIOLET)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            // Stays put until one of the actions is used, so a reminder cannot
            // be swiped away and forgotten.
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(action(context, "Done", ACTION_DONE, habitId, dateKey, title, body))
            .addAction(action(context, "In an hour", ACTION_SNOOZE, habitId, dateKey, title, body))
            .addAction(action(context, "Dismiss", ACTION_DISMISS, habitId, dateKey, title, body))
            .build()

        try {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(notificationId(habitId), notification)
        } catch (e: SecurityException) {
            // Notifications permission revoked, nothing to do.
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        Reminders.post(
            context = context.applicationContext,
            habitId = habitId,
            dateKey = intent.getStringExtra(EXTRA_DATE_KEY) ?: LocalDate.now().toString(),
            title = intent.getStringExtra(EXTRA_TITLE) ?: "NizKarya",
            body = intent.getStringExtra(EXTRA_BODY) ?: "Reminder"
        )
    }
}

/** Handles the three buttons on a reminder. */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID) ?: return
        val dateKey = intent.getStringExtra(EXTRA_DATE_KEY) ?: LocalDate.now().toString()
        val manager = context.getSystemService(NotificationManager::class.java)
        val appContext = context.applicationContext

        when (intent.action) {
            ACTION_DISMISS -> manager?.cancel(Reminders.notificationId(habitId))

            ACTION_SNOOZE -> {
                Reminders.snooze(
                    context = appContext,
                    habitId = habitId,
                    dateKey = dateKey,
                    title = intent.getStringExtra(EXTRA_TITLE) ?: "NizKarya",
                    body = intent.getStringExtra(EXTRA_BODY) ?: "Reminder"
                )
                manager?.cancel(Reminders.notificationId(habitId))
            }

            ACTION_DONE -> {
                manager?.cancel(Reminders.notificationId(habitId))
                Reminders.cancelSnooze(appContext, habitId)

                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                // This process can be torn down as soon as onReceive returns, so
                // hold it open while the write lands. Firestore keeps a local
                // queue, so the tick survives being offline either way.
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        withTimeoutOrNull(ACTION_WRITE_TIMEOUT_MS) {
                            HabitRepo.markDoneOn(uid, habitId, dateKey)
                        }
                    } catch (e: Exception) {
                        // Nothing useful to surface from a receiver; the local
                        // write queue retries on its own.
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
