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
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.widget.WidgetRefresh
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Brand violet, used to tint the notification's small icon. */
private const val BRAND_VIOLET = 0xFF8B7CF6.toInt()

/**
 * A ceiling on the whole Done handler, not a wait for the server.
 *
 * The write and the redraw both work off Firestore's local cache now, so this
 * only exists so a receiver can never be held open by something unexpected.
 */
private const val ACTION_WRITE_TIMEOUT_MS = 4_000L

/**
 * A ceiling on checking that a reminder is still about something real. Off the
 * cache this is instant; the budget is for the rare case that has to ask the
 * server, and a receiver only gets about ten seconds in total.
 */
private const val SUBJECT_CHECK_TIMEOUT_MS = 4_000L

const val EXTRA_KIND = "kind"
const val EXTRA_ID = "id"
const val EXTRA_DATE_KEY = "dateKey"
const val EXTRA_TITLE = "title"
const val EXTRA_BODY = "body"

const val KIND_HABIT = "habit"
const val KIND_TODO = "todo"

const val ACTION_DISMISS = "com.nizkarya.app.DISMISS"
const val ACTION_DONE = "com.nizkarya.app.MARK_DONE"
const val ACTION_SNOOZE = "com.nizkarya.app.SNOOZE"

/**
 * On-device reminders for habits and tasks. No server, no FCM.
 *
 * Everything is (re)scheduled in whole passes by [ReminderScheduler] rather
 * than incrementally, so a pass is idempotent and self-correcting: anything
 * that should no longer fire gets cancelled by the same loop that arms the
 * rest.
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

    // ── Alarm identity ───────────────────────────────────────────────────────
    // Habits repeat, so their slot is per day: scheduling today and tomorrow
    // under one code would have the second overwrite the first. Tasks fire
    // once, so the id alone is enough. Snoozes need their own slot entirely,
    // or a scheduling pass would see the original time in the past, decide the
    // reminder should not fire, and cancel the snooze along with it.
    //
    // The key is the string, and the request code is its hash. Writing it this
    // way round means a pass can record what it armed and a later pass can
    // cancel it from the record alone, which is the only way to reach an alarm
    // whose task or habit no longer exists to be walked.

    fun notificationId(kind: String, id: String): Int = ("$kind|$id").hashCode()

    fun habitKey(habitId: String, dateKey: String) = "h|$habitId|$dateKey"

    fun todoKey(todoId: String) = "t|$todoId"

    private fun snoozeCode(kind: String, id: String) = ("s|$kind|$id").hashCode()

    private fun reminderIntent(
        context: Context,
        kind: String,
        id: String,
        dateKey: String,
        title: String,
        body: String
    ) = Intent(context, ReminderReceiver::class.java)
        .putExtra(EXTRA_KIND, kind)
        .putExtra(EXTRA_ID, id)
        .putExtra(EXTRA_DATE_KEY, dateKey)
        .putExtra(EXTRA_TITLE, title)
        .putExtra(EXTRA_BODY, body)

    private fun broadcast(context: Context, code: Int, intent: Intent) =
        PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

    // ── Scheduling ───────────────────────────────────────────────────────────

    /**
     * Arm or cancel one habit's reminder for [date]. Called for every day in
     * the rolling window so that missing a day of app usage no longer means
     * missing the reminder.
     */
    fun syncHabit(context: Context, habit: Habit, date: LocalDate): String? {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return null
        val zone = HabitLogic.zoneOf(habit.timezone)
        val dateKey = date.toString()
        val key = habitKey(habit.id, dateKey)
        val body = "Time for this one. Keep your streak going."
        val pending = broadcast(
            context,
            key.hashCode(),
            reminderIntent(context, KIND_HABIT, habit.id, dateKey, habit.title, body)
        )

        val time = runCatching { LocalTime.parse(habit.reminderTime) }.getOrNull()
        val triggerAt = time?.let { date.atTime(it).atZone(zone).toInstant().toEpochMilli() }
        val shouldFire = habit.archivedAt == null &&
            triggerAt != null &&
            triggerAt > System.currentTimeMillis() &&
            HabitLogic.isScheduledOn(habit, date) &&
            dateKey !in habit.completionDates &&
            dateKey !in habit.skippedDates

        if (!shouldFire) {
            alarmManager.cancel(pending)
            return null
        }
        setAlarm(context, triggerAt!!, pending)
        return key
    }

    /**
     * Arm or cancel one task's reminder. Whether it should fire is decided by
     * [ReminderScheduler], which sees the whole list and so can also apply the
     * cap on how many alarms are held at once.
     */
    fun syncTodo(context: Context, todo: Todo, shouldFire: Boolean): String? {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return null
        val dateKey = todo.scheduledDate?.toDate()?.toInstant()
            ?.atZone(ZoneId.systemDefault())?.toLocalDate()?.toString()
            ?: LocalDate.now().toString()
        val key = todoKey(todo.id)
        val pending = broadcast(
            context,
            key.hashCode(),
            reminderIntent(context, KIND_TODO, todo.id, dateKey, todo.title, "Scheduled for now.")
        )

        val triggerAt = todo.scheduledDate?.toDate()?.time
        if (!shouldFire || triggerAt == null) {
            // The snooze is deliberately left alone here. A task whose time has
            // simply passed is not armed either, and taking the snooze with it
            // would mean the next scheduling pass quietly undid "In an hour".
            // Snoozes are dropped in [settle], once the task is really done.
            alarmManager.cancel(pending)
            return null
        }
        setAlarm(context, triggerAt, pending)
        return key
    }

    /**
     * Cancel an alarm from the key a scheduling pass recorded for it.
     *
     * This is the only way to reach an alarm whose task or habit has been
     * deleted. A pass can only walk what still exists, so a deleted item's
     * alarm was never visited and stayed armed until it woke the phone and
     * announced something that was no longer there.
     */
    fun cancelArmed(context: Context, key: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(
            broadcast(context, key.hashCode(), Intent(context, ReminderReceiver::class.java))
        )
    }

    /**
     * This one is finished, archived or gone: drop its snooze and take down any
     * notification of it still sitting in the shade.
     */
    fun settle(context: Context, kind: String, id: String) {
        cancelSnooze(context, kind, id)
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(notificationId(kind, id))
    }

    /**
     * Is this reminder still about something real, at the moment it fires?
     *
     * The backstop for every way an alarm can outlive its subject: a delete, an
     * archive or a tick made on another device, a stale alarm armed by an older
     * build. Cancelling properly is still worth doing, since a wakeup avoided
     * is battery saved, but only a check here can be relied on.
     *
     * It fails open on purpose. A lookup that cannot reach a conclusion returns
     * true, because a reminder you did not want is a smaller harm than one you
     * needed and never got.
     */
    suspend fun stillDue(kind: String, id: String, dateKey: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return true
        return if (kind == KIND_TODO) {
            TodoRepo.lookup(uid, id).fold(
                onSuccess = { it != null && it.archivedAt == null && it.status == "pending" },
                onFailure = { true }
            )
        } else {
            HabitRepo.lookup(uid, id).fold(
                onSuccess = {
                    it != null && it.archivedAt == null &&
                        dateKey !in it.completionDates && dateKey !in it.skippedDates
                },
                onFailure = { true }
            )
        }
    }

    /** Re-arm one reminder an hour out, keeping its original day. */
    fun snooze(
        context: Context,
        kind: String,
        id: String,
        dateKey: String,
        title: String,
        body: String
    ) {
        setAlarm(
            context,
            System.currentTimeMillis() + SNOOZE_MILLIS,
            broadcast(
                context,
                snoozeCode(kind, id),
                reminderIntent(context, kind, id, dateKey, title, body)
            )
        )
    }

    fun cancelSnooze(context: Context, kind: String, id: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(
            broadcast(context, snoozeCode(kind, id), Intent(context, ReminderReceiver::class.java))
        )
    }

    // ── Posting ──────────────────────────────────────────────────────────────

    private fun action(
        context: Context,
        label: String,
        action: String,
        kind: String,
        id: String,
        dateKey: String,
        title: String,
        body: String
    ): Notification.Action {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_DATE_KEY, dateKey)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_BODY, body)
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_notification),
            label,
            // Distinct per kind, per item and per action, or they would collide.
            broadcast(context, ("$kind|$id|$action").hashCode(), intent)
        ).build()
    }

    fun post(context: Context, kind: String, id: String, dateKey: String, title: String, body: String) {
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            notificationId(kind, id),
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
            // Stays put until an action is used, so a reminder cannot be
            // swiped away and forgotten.
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(action(context, "Done", ACTION_DONE, kind, id, dateKey, title, body))
            .addAction(action(context, "In an hour", ACTION_SNOOZE, kind, id, dateKey, title, body))
            .addAction(action(context, "Dismiss", ACTION_DISMISS, kind, id, dateKey, title, body))
            .build()

        try {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(notificationId(kind, id), notification)
        } catch (e: SecurityException) {
            // Notifications permission revoked, nothing to do.
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_HABIT
        val dateKey = intent.getStringExtra(EXTRA_DATE_KEY) ?: LocalDate.now().toString()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "NizKarya"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "Reminder"
        val appContext = context.applicationContext

        // An alarm carries a copy of the title and the time it was armed with,
        // and nothing else. It has no idea whether the task still exists. So
        // ask, before saying anything out loud.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val due = try {
                withTimeoutOrNull(SUBJECT_CHECK_TIMEOUT_MS) {
                    Reminders.stillDue(kind, id, dateKey)
                } ?: true
            } catch (e: Exception) {
                true
            }
            try {
                if (due) {
                    Reminders.post(appContext, kind, id, dateKey, title, body)
                } else {
                    // The alarm outlived its subject, so clear up after it
                    // rather than leaving the next one to fire too.
                    Reminders.settle(appContext, kind, id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Handles the three buttons on a reminder. */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_HABIT
        val dateKey = intent.getStringExtra(EXTRA_DATE_KEY) ?: LocalDate.now().toString()
        val manager = context.getSystemService(NotificationManager::class.java)
        val appContext = context.applicationContext

        when (intent.action) {
            ACTION_DISMISS -> manager?.cancel(Reminders.notificationId(kind, id))

            ACTION_SNOOZE -> {
                Reminders.snooze(
                    context = appContext,
                    kind = kind,
                    id = id,
                    dateKey = dateKey,
                    title = intent.getStringExtra(EXTRA_TITLE) ?: "NizKarya",
                    body = intent.getStringExtra(EXTRA_BODY) ?: "Reminder"
                )
                manager?.cancel(Reminders.notificationId(kind, id))
            }

            ACTION_DONE -> {
                manager?.cancel(Reminders.notificationId(kind, id))
                Reminders.cancelSnooze(appContext, kind, id)

                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                // This process can be torn down as soon as onReceive returns,
                // so hold it open across the write and the redraw that follows
                // it. Both are local: Firestore applies the change to its own
                // cache and replays it to the server on its own time, and the
                // widgets are drawn from that same cache. Waiting for the
                // server here is what used to make Done from a notification
                // take minutes, because the acknowledgement outlived the ten
                // seconds a receiver is given and took the redraw down with it.
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        withTimeoutOrNull(ACTION_WRITE_TIMEOUT_MS) {
                            if (kind == KIND_TODO) {
                                TodoRepo.completeByIdLocal(uid, id)
                            } else {
                                HabitRepo.markDoneOnLocal(uid, id, dateKey)
                            }
                            WidgetRefresh.refreshAll(appContext)
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
