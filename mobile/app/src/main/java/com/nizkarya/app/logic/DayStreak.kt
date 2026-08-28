package com.nizkarya.app.logic

import com.nizkarya.app.data.Todo
import java.time.LocalDate
import java.time.ZoneId

/**
 * Day streak: consecutive calendar days (walking back from today) with at
 * least one completed task. An empty *today* doesn't break the streak.
 */
object DayStreak {

    fun completionDays(todos: List<Todo>, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
        todos.mapNotNull { todo ->
            todo.completedDate?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate()
        }.toSet()

    fun current(todos: List<Todo>, today: LocalDate = LocalDate.now()): Int {
        val days = completionDays(todos)
        var streak = 0
        var date = today
        var checked = 0
        while (checked < 366) {
            when {
                date in days -> streak++
                date == today -> { /* today still in progress, doesn't break */ }
                else -> return streak
            }
            date = date.minusDays(1)
            checked++
        }
        return streak
    }
}
