package com.nizkarya.app

import com.google.firebase.Timestamp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import com.nizkarya.app.notifications.KIND_HABIT
import com.nizkarya.app.notifications.KIND_TODO
import com.nizkarya.app.notifications.ReminderScheduler
import com.nizkarya.app.notifications.Reminders
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides whether a reminder still has anything to be about.
 *
 * Worth pinning, because getting it wrong is not a cosmetic bug: too strict and
 * you are told about work you deleted, too loose and a reminder you needed goes
 * quiet.
 */
class ReminderSettleTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val todayKey: String = LocalDate.now(zone).toString()

    private fun todo(id: String, status: String = "pending", archived: Boolean = false) = Todo(
        id = id,
        title = id,
        status = status,
        scheduledDate = null,
        completedDate = null,
        archivedAt = if (archived) Timestamp(Date()) else null,
        createdAt = null,
        priority = "medium",
        tags = emptyList(),
        contextTags = emptyList(),
        description = "",
        recurrence = null,
        subtasks = emptyList()
    )

    private fun habit(
        id: String,
        kept: List<String> = emptyList(),
        skipped: List<String> = emptyList(),
        archived: Boolean = false
    ) = Habit(
        id = id,
        title = id,
        habitType = "positive",
        reminderTime = "07:00",
        reminderDays = listOf(0, 1, 2, 3, 4, 5, 6),
        completionDates = kept,
        skippedDates = skipped,
        timezone = zone.id,
        frequency = "daily",
        graceMisses = 0,
        contextTags = emptyList(),
        triggerAfterHabitId = null,
        createdAt = null,
        archivedAt = if (archived) Timestamp(Date()) else null
    )

    private fun ownerOfTodo(id: String) =
        ReminderScheduler.ownerOf(Reminders.todoKey(id))!!

    private fun ownerOfHabit(id: String) =
        ReminderScheduler.ownerOf(Reminders.habitKey(id, todayKey))!!

    @Test
    fun `a key names the task or habit it was armed for`() {
        assertEquals(
            KIND_TODO to "abc",
            ReminderScheduler.ownerOf(Reminders.todoKey("abc"))
        )
        assertEquals(
            KIND_HABIT to "xyz",
            ReminderScheduler.ownerOf(Reminders.habitKey("xyz", todayKey))
        )
    }

    @Test
    fun `a key that is not one of ours names nothing`() {
        assertNull(ReminderScheduler.ownerOf("nonsense"))
        assertNull(ReminderScheduler.ownerOf("t|"))
        assertNull(ReminderScheduler.ownerOf(""))
    }

    @Test
    fun `a deleted task is settled because there is nothing left to announce`() {
        assertTrue(
            ReminderScheduler.isSettled(ownerOfTodo("gone"), emptyList(), emptyList())
        )
    }

    @Test
    fun `an archived task is settled`() {
        val todos = listOf(todo("a", archived = true))
        assertTrue(ReminderScheduler.isSettled(ownerOfTodo("a"), emptyList(), todos))
    }

    @Test
    fun `a completed task is settled`() {
        val todos = listOf(todo("a", status = "completed"))
        assertTrue(ReminderScheduler.isSettled(ownerOfTodo("a"), emptyList(), todos))
    }

    @Test
    fun `a task still waiting to be done is not settled`() {
        val todos = listOf(todo("a"))
        assertFalse(ReminderScheduler.isSettled(ownerOfTodo("a"), emptyList(), todos))
    }

    @Test
    fun `a deleted habit is settled`() {
        assertTrue(
            ReminderScheduler.isSettled(ownerOfHabit("gone"), emptyList(), emptyList())
        )
    }

    @Test
    fun `an archived habit is settled`() {
        val habits = listOf(habit("h", archived = true))
        assertTrue(ReminderScheduler.isSettled(ownerOfHabit("h"), habits, emptyList()))
    }

    @Test
    fun `a habit already kept today is settled`() {
        val habits = listOf(habit("h", kept = listOf(todayKey)))
        assertTrue(ReminderScheduler.isSettled(ownerOfHabit("h"), habits, emptyList()))
    }

    @Test
    fun `a habit skipped today is settled`() {
        val habits = listOf(habit("h", skipped = listOf(todayKey)))
        assertTrue(ReminderScheduler.isSettled(ownerOfHabit("h"), habits, emptyList()))
    }

    @Test
    fun `a habit kept on some other day is still due today`() {
        val habits = listOf(habit("h", kept = listOf(LocalDate.now(zone).minusDays(1).toString())))
        assertFalse(ReminderScheduler.isSettled(ownerOfHabit("h"), habits, emptyList()))
    }

    @Test
    fun `an untouched habit is not settled`() {
        val habits = listOf(habit("h"))
        assertFalse(ReminderScheduler.isSettled(ownerOfHabit("h"), habits, emptyList()))
    }
}
