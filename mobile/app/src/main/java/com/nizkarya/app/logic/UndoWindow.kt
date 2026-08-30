package com.nizkarya.app.logic

import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * When a finished thing stops being editable.
 *
 * Ticking something off is a record of what happened, not a toggle to play
 * with. Once its moment has gone by, the record settles: the app will not let
 * you reopen it, by tap or by swipe, because doing so would rewrite the past
 * and quietly reshape every streak and percentage built on top of it.
 *
 * The cut-off is the thing's own due moment, not the end of the day. A task
 * due at 09:00 and ticked at 09:05 is settled at 09:05.
 *
 * Nothing here blocks the reverse direction. An unfinished thing can still be
 * completed late, and an untouched past day can still be filled in, because
 * neither of those erases anything.
 */
object UndoWindow {

    /** A task with no scheduled moment has no deadline to pass, so it stays open. */
    fun todoDeadline(todo: Todo): Instant? =
        todo.scheduledDate?.toDate()?.toInstant()

    /**
     * True when [todo] is finished and its scheduled moment has gone by, so it
     * can no longer be reopened.
     */
    fun isTodoSettled(todo: Todo, now: Instant = Instant.now()): Boolean {
        if (todo.status != "completed") return false
        val deadline = todoDeadline(todo) ?: return false
        return !deadline.isAfter(now)
    }

    /**
     * When a habit's slot on [date] closes.
     *
     * With a reminder time, that time is the deadline. Without one the habit
     * is owed sometime that day, so the slot runs to midnight. Both are read
     * in the habit's own zone, since that is the zone its completion keys were
     * written in.
     */
    fun habitDeadline(habit: Habit, date: LocalDate): Instant {
        val zone = HabitLogic.zoneOf(habit.timezone)
        val at = habit.reminderTime.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        return if (at != null) {
            date.atTime(at).atZone(zone).toInstant()
        } else {
            date.plusDays(1).atStartOfDay(zone).toInstant()
        }
    }

    /**
     * True when [habit] is ticked for [date] and that day's slot has closed,
     * so the tick can no longer be taken back.
     */
    fun isHabitSettled(
        habit: Habit,
        date: LocalDate,
        now: Instant = Instant.now()
    ): Boolean {
        if (date.toString() !in habit.completionDates) return false
        return !habitDeadline(habit, date).isAfter(now)
    }

    /** Shown whenever someone tries to reopen something that has settled. */
    const val MESSAGE = "Its time has passed, so this one stays done."
}
