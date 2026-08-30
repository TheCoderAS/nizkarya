package com.nizkarya.app.widget

import com.google.firebase.auth.FirebaseAuth
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.HabitLogic
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One line on a widget: a task or a habit, already reduced to what draws. */
data class WidgetRow(
    val id: String,
    val isHabit: Boolean,
    val title: String,
    val time: String?,
    val done: Boolean,
    val late: Boolean
)

/**
 * Everything the widgets draw, resolved once per update.
 *
 * A widget cannot hold a Firestore listener, so it reads on each update
 * instead. Those reads come out of Firestore's local cache when the phone is
 * offline, which is the same cache the reminder scheduler already depends on.
 */
data class WidgetSnapshot(
    val signedIn: Boolean,
    val dateLabel: String,
    val done: Int,
    val total: Int,
    val rows: List<WidgetRow>,
    val habits: List<WidgetRow>
) {
    val allDone: Boolean get() = total > 0 && done == total

    companion object {
        val SignedOut = WidgetSnapshot(false, "", 0, 0, emptyList(), emptyList())
    }
}

object WidgetData {

    private val dateLabel = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Signed out is a state a widget has to draw, not a crash. It is also the
     * state right after an install, which is when someone is most likely to be
     * looking at the thing.
     */
    suspend fun load(): WidgetSnapshot {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return WidgetSnapshot.SignedOut
        return runCatching { build(uid) }.getOrElse { WidgetSnapshot.SignedOut }
    }

    private suspend fun build(uid: String): WidgetSnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = LocalTime.now(zone)

        val todos = TodoRepo.fetchAll(uid).filter { it.archivedAt == null }
        val habits = HabitRepo.fetchAll(uid).filter { it.archivedAt == null }

        val todayTodos = todos.filter { todo ->
            val at = todo.scheduledDate?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate()
            at == today
        }
        val todayHabits = habits.filter { HabitLogic.isScheduledToday(it) }

        val taskRows = todayTodos.map { todo ->
            val at = todo.scheduledDate?.toDate()?.toInstant()?.atZone(zone)?.toLocalTime()
            val complete = todo.status == "completed"
            WidgetRow(
                id = todo.id,
                isHabit = false,
                title = todo.title,
                time = at?.format(clock),
                done = complete,
                late = !complete && at != null && at < now
            )
        }
        val habitRows = todayHabits.map { habit ->
            val at = habit.reminderTime.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            WidgetRow(
                id = habit.id,
                isHabit = true,
                title = habit.title,
                time = at?.format(clock),
                done = HabitLogic.isDoneToday(habit),
                late = false
            )
        }

        // Unfinished first and in time order, because a widget is a short list
        // and the next thing to do has to be in it. Finished work keeps its
        // place in the count but sinks to the bottom.
        val rows = (taskRows + habitRows).sortedWith(
            compareBy<WidgetRow> { it.done }
                .thenBy { it.time ?: "99:99" }
                .thenBy { it.title.lowercase() }
        )

        return WidgetSnapshot(
            signedIn = true,
            dateLabel = today.format(dateLabel),
            done = rows.count { it.done },
            total = rows.size,
            rows = rows,
            habits = habitRows.sortedWith(
                compareBy<WidgetRow> { it.done }.thenBy { it.time ?: "99:99" }
            )
        )
    }
}
