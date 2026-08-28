package com.nizkarya.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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

    /** How far ahead to arm alarms. Comfortably longer than the worker period. */
    private const val WINDOW_DAYS = 3L

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

        val windowEnd = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(WINDOW_DAYS)
        todos.forEach { todo -> Reminders.syncTodo(context, todo, windowEnd) }
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
            PeriodicWorkRequestBuilder<ReminderSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        // Reads come from Firestore's local cache when offline,
                        // but a connection gets us the current picture.
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
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
