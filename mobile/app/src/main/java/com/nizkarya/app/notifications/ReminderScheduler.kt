package com.nizkarya.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.HabitLogic
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

    /** The same pass, for callers that already hold the data from a listener. */
    fun sync(context: Context, habits: List<Habit>, todos: List<Todo>) {
        Reminders.ensureChannel(context)

        habits.forEach { habit ->
            val today = LocalDate.now(HabitLogic.zoneOf(habit.timezone))
            for (offset in 0 until WINDOW_DAYS) {
                Reminders.syncHabit(context, habit, today.plusDays(offset))
            }
        }

        // Decide the armed set first, then walk every task so anything outside
        // it is actively cancelled. Skipping that would strand alarms for
        // tasks that were completed, rescheduled or deleted.
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

        todos.forEach { todo -> Reminders.syncTodo(context, todo, todo.id in armed) }
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
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
