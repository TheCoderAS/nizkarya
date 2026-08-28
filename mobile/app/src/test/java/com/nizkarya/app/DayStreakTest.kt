package com.nizkarya.app

import com.google.firebase.Timestamp
import com.nizkarya.app.data.Todo
import com.nizkarya.app.logic.DayStreak
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class DayStreakTest {

    private fun todoCompletedOn(date: LocalDate): Todo {
        val instant = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        return Todo(
            id = date.toString(),
            title = "t",
            status = "completed",
            scheduledDate = null,
            completedDate = Timestamp(Date.from(instant)),
            archivedAt = null,
            createdAt = null,
            priority = "medium",
            tags = emptyList(),
            contextTags = emptyList(),
            description = "",
            recurrence = null,
            subtasks = emptyList()
        )
    }

    private val today = LocalDate.of(2026, 5, 31)

    @Test
    fun `empty history is zero`() {
        assertEquals(0, DayStreak.current(emptyList(), today))
    }

    @Test
    fun `counts consecutive days and an empty today does not break`() {
        val todos = listOf(
            todoCompletedOn(today.minusDays(1)),
            todoCompletedOn(today.minusDays(2))
        )
        assertEquals(2, DayStreak.current(todos, today))
    }

    @Test
    fun `today counts when it has a completion`() {
        val todos = listOf(
            todoCompletedOn(today),
            todoCompletedOn(today.minusDays(1))
        )
        assertEquals(2, DayStreak.current(todos, today))
    }

    @Test
    fun `a gap breaks the streak`() {
        val todos = listOf(
            todoCompletedOn(today.minusDays(1)),
            todoCompletedOn(today.minusDays(3))
        )
        assertEquals(1, DayStreak.current(todos, today))
    }
}
