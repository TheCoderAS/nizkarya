package com.nizkarya.app

import com.google.firebase.Timestamp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import com.nizkarya.app.logic.UndoWindow
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoWindowTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 5, 31)

    private fun instant(date: LocalDate, hour: Int, minute: Int = 0) =
        date.atTime(hour, minute).atZone(zone).toInstant()

    private fun todo(
        status: String,
        scheduledAt: java.time.Instant?
    ) = Todo(
        id = "t",
        title = "t",
        status = status,
        scheduledDate = scheduledAt?.let { Timestamp(Date.from(it)) },
        completedDate = null,
        archivedAt = null,
        createdAt = null,
        priority = "medium",
        tags = emptyList(),
        contextTags = emptyList(),
        description = "",
        recurrence = null,
        subtasks = emptyList()
    )

    private fun habit(
        reminderTime: String,
        completionDates: List<String>
    ) = Habit(
        id = "h",
        title = "h",
        habitType = "positive",
        reminderTime = reminderTime,
        reminderDays = listOf(0, 1, 2, 3, 4, 5, 6),
        completionDates = completionDates,
        skippedDates = emptyList(),
        timezone = zone.id,
        frequency = "daily",
        graceMisses = 0,
        contextTags = emptyList(),
        triggerAfterHabitId = null,
        createdAt = null,
        archivedAt = null
    )

    // ── Tasks ────────────────────────────────────────────────────────────────

    @Test
    fun `a pending task is never settled`() {
        val t = todo("pending", instant(day, 9))
        assertFalse(UndoWindow.isTodoSettled(t, instant(day, 23)))
    }

    @Test
    fun `a done task before its time can still be reopened`() {
        val t = todo("completed", instant(day, 9))
        assertFalse(UndoWindow.isTodoSettled(t, instant(day, 8, 59)))
    }

    @Test
    fun `a done task settles the minute its time passes`() {
        val t = todo("completed", instant(day, 9))
        assertTrue(UndoWindow.isTodoSettled(t, instant(day, 9, 1)))
    }

    @Test
    fun `a done task settles exactly on its due moment`() {
        val t = todo("completed", instant(day, 9))
        assertTrue(UndoWindow.isTodoSettled(t, instant(day, 9)))
    }

    @Test
    fun `a done task with no scheduled time never settles`() {
        val t = todo("completed", null)
        assertFalse(UndoWindow.isTodoSettled(t, instant(day.plusYears(1), 12)))
    }

    @Test
    fun `a skipped task is not settled`() {
        val t = todo("skipped", instant(day, 9))
        assertFalse(UndoWindow.isTodoSettled(t, instant(day, 23)))
    }

    // ── Habits ───────────────────────────────────────────────────────────────

    @Test
    fun `an unticked habit day is never settled`() {
        val h = habit("07:00", emptyList())
        assertFalse(UndoWindow.isHabitSettled(h, day, instant(day, 23)))
    }

    @Test
    fun `a habit ticked before its reminder can still be unticked`() {
        val h = habit("21:00", listOf(day.toString()))
        assertFalse(UndoWindow.isHabitSettled(h, day, instant(day, 8)))
    }

    @Test
    fun `a habit settles once its reminder time passes`() {
        val h = habit("07:00", listOf(day.toString()))
        assertTrue(UndoWindow.isHabitSettled(h, day, instant(day, 7, 5)))
    }

    @Test
    fun `a habit with no reminder time runs to midnight`() {
        val h = habit("", listOf(day.toString()))
        assertFalse(UndoWindow.isHabitSettled(h, day, instant(day, 23, 59)))
        assertTrue(UndoWindow.isHabitSettled(h, day, instant(day.plusDays(1), 0)))
    }

    @Test
    fun `a habit ticked on a past day is always settled`() {
        val yesterday = day.minusDays(1)
        val h = habit("21:00", listOf(yesterday.toString()))
        assertTrue(UndoWindow.isHabitSettled(h, yesterday, instant(day, 1)))
    }

    @Test
    fun `the deadline is read in the habit's own zone`() {
        val h = habit("07:00", listOf(day.toString()))
        assertEquals(instant(day, 7), UndoWindow.habitDeadline(h, day))
    }
}
