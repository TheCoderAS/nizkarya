package com.nizkarya.app.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nizkarya.app.MainActivity
import com.nizkarya.app.R
import com.nizkarya.app.data.Habit
import com.nizkarya.app.logic.HabitLogic
import java.time.LocalDate
import java.time.LocalTime

/**
 * On-device reminders, with no server or FCM involved. Habit reminders for today
 * are (re)scheduled whenever the habit list changes while the app is open;
 * exact alarms are used when permitted, with a windowed fallback.
 */
/** Brand violet, used to tint the notification's small icon. */
private const val BRAND_VIOLET = 0xFF8B7CF6.toInt()

object Reminders {

    const val CHANNEL_ID = "reminders"

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

    fun scheduleHabitReminders(context: Context, habits: List<Habit>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        habits.forEach { habit ->
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra("title", habit.title)
                .putExtra("body", "Time for this one. Keep your streak going.")
            val pending = PendingIntent.getBroadcast(
                context,
                habit.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val zone = HabitLogic.zoneOf(habit.timezone)
            val today = LocalDate.now(zone)
            val time = runCatching { LocalTime.parse(habit.reminderTime) }.getOrNull()
            val triggerAt = time?.let {
                today.atTime(it).atZone(zone).toInstant().toEpochMilli()
            }
            val shouldFire = habit.archivedAt == null &&
                triggerAt != null &&
                triggerAt > System.currentTimeMillis() &&
                HabitLogic.isScheduledOn(habit, today) &&
                today.toString() !in habit.completionDates

            if (!shouldFire) {
                // Habit was completed, archived, or unscheduled, so drop any
                // alarm that may already be set so it can't fire stale.
                alarmManager.cancel(pending)
                return@forEach
            }

            try {
                val canExact =
                    Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt!!, pending
                    )
                } else {
                    alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP, triggerAt!!, 10 * 60 * 1000L, pending
                    )
                }
            } catch (e: SecurityException) {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP, triggerAt!!, 10 * 60 * 1000L, pending
                )
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.ensureChannel(context)
        val title = intent.getStringExtra("title") ?: "NizKarya"
        val body = intent.getStringExtra("body") ?: "Reminder"

        // Tapping the reminder should open the app, which it previously did not.
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, Reminders.CHANNEL_ID)
            // Was android.R.drawable.ic_popup_reminder, a stock Android glyph,
            // which is why notifications carried no NizKarya identity at all.
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_VIOLET)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(title.hashCode(), notification)
        } catch (e: SecurityException) {
            // Notifications permission revoked, nothing to do.
        }
    }
}
