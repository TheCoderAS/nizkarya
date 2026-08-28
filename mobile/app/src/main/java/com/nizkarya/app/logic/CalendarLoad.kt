package com.nizkarya.app.logic

import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import java.time.LocalDate
import java.time.ZoneId

/** What sits on one day, used for the calendar's dots and day headers. */
data class DayLoad(
    val tasks: Int = 0,
    val tasksDone: Int = 0,
    val habits: Int = 0,
    val habitsDone: Int = 0
) {
    val total: Int get() = tasks + habits
    val done: Int get() = tasksDone + habitsDone
    val allDone: Boolean get() = total > 0 && done == total
}

object CalendarLoad {

    /**
     * Load for [days] consecutive dates from [start].
     *
     * Computed in a single walk of the data rather than per cell: a month grid
     * asks about 42 days, and habit scheduling parses a time zone on every
     * check, so the per-cell version would be dramatically more expensive.
     */
    fun forRange(
        todos: List<Todo>,
        habits: List<Habit>,
        start: LocalDate,
        days: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, DayLoad> {
        if (days <= 0) return emptyMap()
        val end = start.plusDays((days - 1).toLong())
        val result = HashMap<LocalDate, DayLoad>(days)

        todos.forEach { todo ->
            if (todo.archivedAt != null) return@forEach
            val scheduled = todo.scheduledDate ?: return@forEach
            val date = scheduled.toDate().toInstant().atZone(zone).toLocalDate()
            if (date.isBefore(start) || date.isAfter(end)) return@forEach
            val current = result[date] ?: DayLoad()
            result[date] = current.copy(
                tasks = current.tasks + 1,
                tasksDone = current.tasksDone + if (todo.status == "completed") 1 else 0
            )
        }

        habits.forEach { habit ->
            if (habit.archivedAt != null) return@forEach
            // A habit cannot have been due before it existed, so days earlier
            // than its creation are not misses waiting to be shown.
            val created = habit.createdAt?.toDate()?.toInstant()
                ?.atZone(HabitLogic.zoneOf(habit.timezone))?.toLocalDate()
            var date = start
            while (!date.isAfter(end)) {
                val tooEarly = created != null && date.isBefore(created)
                if (!tooEarly && HabitLogic.isScheduledOn(habit, date)) {
                    val current = result[date] ?: DayLoad()
                    result[date] = current.copy(
                        habits = current.habits + 1,
                        habitsDone = current.habitsDone +
                            if (date.toString() in habit.completionDates) 1 else 0
                    )
                }
                date = date.plusDays(1)
            }
        }

        return result
    }
}
