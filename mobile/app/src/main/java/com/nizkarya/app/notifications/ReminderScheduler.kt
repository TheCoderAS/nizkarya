package com.nizkarya.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.widget.WidgetRefresh
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Rebuilds every alarm in one pass.
 *
 * Reminders used to be armed only from a composable while the app was open,
 * and only for the current day, which meant a day without opening the app was
 * a day without reminders, and a reboot wiped them entirely. Scheduling now
 * runs from three places that do not depend on the UI: app start, a periodic
 * worker, and [BootReceiver].
 */
object ReminderScheduler {

    /**
     * How far ahead to arm alarms. Much longer than the worker period on
     * purpose: OEM battery managers routinely defer background work, and a
     * week of slack means several skipped runs still cost nothing.
     */
    private const val WINDOW_DAYS = 7L

    /**
     * Ceiling on task alarms held at once. Android caps how many exact alarms
     * an app may keep, so a very full week degrades by arming the soonest
     * rather than by failing outright.
     */
    private const val MAX_TODO_ALARMS = 100

    private const val WORK_NAME = "nizkarya-reminders"

    /**
     * Where the last pass records what it armed.
     *
     * A pass can only walk the tasks and habits that still exist, so on its own
     * it can never cancel the alarm for one that was deleted: there is nothing
     * left to iterate. Keeping the keys means the next pass can see what has
     * disappeared and reach those alarms anyway.
     */
    private const val PREFS = "nizkarya-alarms"
    private const val KEY_ARMED = "armed-keys"

    /**
     * Reads the current data and arms or cancels every alarm accordingly.
     * Safe to call repeatedly: each pass fully determines the alarm state, so
     * anything that should no longer fire is cancelled by the same loop.
     */
    suspend fun syncAll(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val habits = runCatching { HabitRepo.fetchAll(uid) }.getOrNull() ?: return
        val todos = runCatching { TodoRepo.fetchAll(uid) }.getOrNull() ?: return
        sync(context, habits, todos)
    }

    /** Arming alarms is binder traffic, so keep it off whatever thread called. */
    suspend fun sync(context: Context, habits: List<Habit>, todos: List<Todo>) {
        withContext(Dispatchers.Default) { syncBlocking(context, habits, todos) }
    }

    /**
     * The pass itself.
     *
     * Every habit-day and every task costs a `PendingIntent.getBroadcast` and
     * an AlarmManager call, and both are binder round trips to system_server.
     * A few hundred of those is seconds of blocked thread, so this must never
     * run on the main thread; [sync] is the entry point that guarantees it.
     */
    private fun syncBlocking(context: Context, habits: List<Habit>, todos: List<Todo>) {
        Reminders.ensureChannel(context)

        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Copied, because the set a SharedPreferences hands back must not be
        // held on to or modified.
        val previous = store.getStringSet(KEY_ARMED, null)?.toSet().orEmpty()
        val current = mutableSetOf<String>()

        habits.forEach { habit ->
            val today = LocalDate.now(HabitLogic.zoneOf(habit.timezone))
            for (offset in 0 until WINDOW_DAYS) {
                Reminders.syncHabit(context, habit, today.plusDays(offset))?.let { current += it }
            }
        }

        // Decide the armed set first, then walk every task so anything outside
        // it is actively cancelled. That covers tasks that were completed or
        // rescheduled, because those are still in the list to be walked.
        val now = System.currentTimeMillis()
        val windowEnd = now + TimeUnit.DAYS.toMillis(WINDOW_DAYS)
        val armed = todos
            .filter { todo ->
                val at = todo.scheduledDate?.toDate()?.time
                todo.archivedAt == null && todo.status == "pending" &&
                    at != null && at > now && at <= windowEnd
            }
            .sortedBy { it.scheduledDate?.toDate()?.time ?: Long.MAX_VALUE }
            .take(MAX_TODO_ALARMS)
            .map { it.id }
            .toSet()

        todos.forEach { todo ->
            Reminders.syncTodo(context, todo, todo.id in armed)?.let { current += it }
        }

        // Deleted work leaves nothing behind to walk, so its alarms are reached
        // through the record instead. Without this a task deleted before its
        // time still woke the phone and announced itself.
        (previous - current).forEach { Reminders.cancelArmed(context, it) }

        // Only something we actually armed can have left a snooze or a
        // notification behind, so that is the set worth checking. Anything in
        // it that is now done, archived or gone loses both.
        previous.mapNotNull { ownerOf(it) }.toSet().forEach { owner ->
            if (isSettled(owner, habits, todos)) {
                Reminders.settle(context, owner.first, owner.second)
            }
        }

        store.edit().putStringSet(KEY_ARMED, current).apply()
    }

    /** The kind and id an alarm key belongs to, or null if it is not one of ours. */
    internal fun ownerOf(key: String): Pair<String, String>? {
        val parts = key.split("|")
        if (parts.size < 2 || parts[1].isEmpty()) return null
        return (if (parts[0] == "h") KIND_HABIT else KIND_TODO) to parts[1]
    }

    /**
     * Is there nothing left for this reminder to be about? Gone, archived, or
     * already dealt with for the day it was armed for.
     */
    internal fun isSettled(
        owner: Pair<String, String>,
        habits: List<Habit>,
        todos: List<Todo>
    ): Boolean {
        val (kind, id) = owner
        return if (kind == KIND_HABIT) {
            val habit = habits.firstOrNull { it.id == id } ?: return true
            val key = LocalDate.now(HabitLogic.zoneOf(habit.timezone)).toString()
            habit.archivedAt != null ||
                key in habit.completionDates ||
                key in habit.skippedDates
        } else {
            val todo = todos.firstOrNull { it.id == id } ?: return true
            todo.archivedAt != null || todo.status != "pending"
        }
    }

    /**
     * Keeps the rolling window topped up without the app being opened. The
     * period is well inside [WINDOW_DAYS], so a missed run is not a missed
     * reminder.
     */
    fun enqueuePeriodicSync(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            // Deliberately unconstrained. Requiring a network would mean no
            // reminders offline, yet Firestore serves these reads from its
            // local cache perfectly well without one.
            PeriodicWorkRequestBuilder<ReminderSyncWorker>(6, TimeUnit.HOURS).build()
        )
    }
}

class ReminderSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            ReminderScheduler.syncAll(applicationContext)
            // syncAll just pulled both collections from the server, so the
            // cache the widgets draw from is as fresh as it will get. Redraw
            // while it is: with the app closed there is no listener, and this
            // is the only thing that brings a change made somewhere else to
            // the home screen.
            WidgetRefresh.refreshAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
