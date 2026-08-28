package com.nizkarya.app

import com.google.firebase.Timestamp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import com.nizkarya.app.logic.CalendarLoad
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarLoadTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val start: LocalDate = LocalDate.of(2026, 3, 2) // a Monday

    private fun at(date: LocalDate, hour: Int = 9): Timestamp =
        Timestamp(Date.from(date.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant()))

    private fun todo(
        id: String = "t1",
        status: String = "pending",
        scheduledDate: Timestamp? = at(start),
        archivedAt: Timestamp? = null
    ) = Todo(
        id = id,
        title = "Task",
        status = status,
        scheduledDate = scheduledDate,
        completedDate = null,
        archivedAt = archivedAt,
        createdAt = null,
        priority = "medium",
        tags = emptyList(),
        contextTags = emptyList(),
        description = "",
        recurrence = null,
        subtasks = emptyList()
    )

    private fun habit(
        id: String = "h1",
        frequency: String = "daily",
        reminderDays: List<Int> = emptyList(),
        completionDates: List<String> = emptyList(),
        createdAt: Timestamp? = null,
        archivedAt: Timestamp? = null
    ) = Habit(
        id = id,
        title = "Habit",
        habitType = "positive",
        reminderTime = "",
        reminderDays = reminderDays,
        completionDates = completionDates,
        skippedDates = emptyList(),
        timezone = "UTC",
        frequency = frequency,
        graceMisses = 0,
        contextTags = emptyList(),
        triggerAfterHabitId = null,
        createdAt = createdAt,
        archivedAt = archivedAt
    )

    @Test
    fun `counts a task on its scheduled day`() {
        val load = CalendarLoad.forRange(listOf(todo()), emptyList(), start, 3, zone)
        assertEquals(1, load[start]?.tasks)
        assertEquals(0, load[start]?.tasksDone)
        assertNull(load[start.plusDays(1)])
    }

    @Test
    fun `counts completed tasks separately`() {
        val load = CalendarLoad.forRange(
            listOf(todo(id = "a"), todo(id = "b", status = "completed")),
            emptyList(), start, 1, zone
        )
        assertEquals(2, load[start]?.tasks)
        assertEquals(1, load[start]?.tasksDone)
        assertEquals(false, load[start]?.allDone)
    }

    @Test
    fun `allDone is true only when everything on the day is done`() {
        val load = CalendarLoad.forRange(
            listOf(todo(status = "completed")), emptyList(), start, 1, zone
        )
        assertTrue(load[start]!!.allDone)
    }

    @Test
    fun `ignores archived tasks and archived habits`() {
        val load = CalendarLoad.forRange(
            listOf(todo(archivedAt = at(start))),
            listOf(habit(archivedAt = at(start))),
            start, 1, zone
        )
        assertNull(load[start])
    }

    @Test
    fun `ignores tasks with no scheduled date`() {
        val load = CalendarLoad.forRange(
            listOf(todo(scheduledDate = null)), emptyList(), start, 1, zone
        )
        assertTrue(load.isEmpty())
    }

    @Test
    fun `ignores days outside the range`() {
        val load = CalendarLoad.forRange(
            listOf(todo(scheduledDate = at(start.plusDays(9)))),
            emptyList(), start, 3, zone
        )
        assertTrue(load.isEmpty())
    }

    @Test
    fun `a daily habit lands on every day in the range`() {
        val load = CalendarLoad.forRange(emptyList(), listOf(habit()), start, 4, zone)
        assertEquals(4, load.size)
        (0..3).forEach { assertEquals(1, load[start.plusDays(it.toLong())]?.habits) }
    }

    @Test
    fun `a weekly habit lands only on its chosen weekdays`() {
        // reminderDays uses Sunday = 0, so 1 is Monday. start is a Monday.
        val load = CalendarLoad.forRange(
            emptyList(), listOf(habit(frequency = "weekly", reminderDays = listOf(1))),
            start, 8, zone
        )
        assertEquals(1, load[start]?.habits)
        assertNull(load[start.plusDays(1)])
        assertEquals(1, load[start.plusDays(7)]?.habits)
    }

    @Test
    fun `a habit is not counted before it was created`() {
        val load = CalendarLoad.forRange(
            emptyList(), listOf(habit(createdAt = at(start.plusDays(2)))), start, 4, zone
        )
        assertNull(load[start])
        assertNull(load[start.plusDays(1)])
        assertEquals(1, load[start.plusDays(2)]?.habits)
    }

    @Test
    fun `completed habit days count towards done`() {
        val load = CalendarLoad.forRange(
            emptyList(),
            listOf(habit(completionDates = listOf(start.toString()))),
            start, 2, zone
        )
        assertEquals(1, load[start]?.habitsDone)
        assertTrue(load[start]!!.allDone)
        assertEquals(0, load[start.plusDays(1)]?.habitsDone)
    }

    @Test
    fun `tasks and habits combine on the same day`() {
        val load = CalendarLoad.forRange(listOf(todo()), listOf(habit()), start, 1, zone)
        assertEquals(2, load[start]?.total)
    }

    @Test
    fun `a non-positive range yields nothing`() {
        assertTrue(CalendarLoad.forRange(listOf(todo()), listOf(habit()), start, 0, zone).isEmpty())
    }
}
