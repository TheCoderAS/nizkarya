package com.nizkarya.app

import com.google.firebase.Timestamp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import com.nizkarya.app.logic.Insight
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Insight replaced three separate derivations that Habits, You and Today each
 * did for themselves. These pin the numbers so the merge did not quietly
 * change any of them.
 */
class InsightTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 5, 31)

    private fun stamp(date: LocalDate) =
        Timestamp(Date.from(date.atTime(12, 0).atZone(zone).toInstant()))

    private fun todo(
        id: String,
        status: String,
        scheduled: LocalDate? = null,
        completed: LocalDate? = null,
        archived: Boolean = false
    ) = Todo(
        id = id,
        title = id,
        status = status,
        scheduledDate = scheduled?.let { stamp(it) },
        completedDate = completed?.let { stamp(it) },
        archivedAt = if (archived) stamp(today) else null,
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
        completionDates: List<String> = emptyList(),
        archived: Boolean = false
    ) = Habit(
        id = id,
        title = id,
        habitType = "positive",
        reminderTime = "",
        reminderDays = listOf(0, 1, 2, 3, 4, 5, 6),
        completionDates = completionDates,
        skippedDates = emptyList(),
        timezone = zone.id,
        frequency = "daily",
        graceMisses = 0,
        contextTags = emptyList(),
        triggerAfterHabitId = null,
        createdAt = null,
        archivedAt = if (archived) stamp(today) else null
    )

    @Test
    fun `empty data gives zeroes and no streaks`() {
        val i = Insight.of(emptyList(), emptyList(), today, zone)
        assertEquals(0, i.habitConsistency)
        assertEquals(0, i.dayStreak)
        assertEquals(0, i.weekTotal)
        assertEquals(emptyMap<String, Int>(), i.streaks)
    }

    @Test
    fun `archived work is left out of every count`() {
        val i = Insight.of(
            todos = listOf(
                todo("a", "pending"),
                todo("b", "pending", archived = true),
                todo("c", "completed", scheduled = today, completed = today, archived = true)
            ),
            habits = listOf(habit("h", archived = true)),
            today = today,
            zone = zone
        )
        assertEquals(1, i.pending)
        assertEquals(0, i.completed)
        assertEquals(0, i.habitCount)
        assertEquals(emptyMap<String, Int>(), i.streaks)
    }

    @Test
    fun `a streak is exposed per habit id`() {
        val kept = (0..4).map { LocalDate.now(zone).minusDays(it.toLong()).toString() }
        val i = Insight.of(emptyList(), listOf(habit("h", kept)), today, zone)
        assertEquals(5, i.streakOf("h"))
        assertEquals(5, i.bestHabitStreak)
        assertEquals(0, i.streakOf("missing"))
    }

    @Test
    fun `a habit ticked today is not counted as still due`() {
        val doneToday = listOf(LocalDate.now(zone).toString())
        val i = Insight.of(emptyList(), listOf(habit("h", doneToday)), today, zone)
        assertEquals(0, i.habitsDueToday)
    }

    @Test
    fun `an untouched habit is still due`() {
        val i = Insight.of(emptyList(), listOf(habit("h")), today, zone)
        assertEquals(1, i.habitsDueToday)
    }

    @Test
    fun `consistency is check-ins over scheduled days in the window`() {
        val kept = (0..14).map { LocalDate.now(zone).minusDays(it.toLong()).toString() }
        val i = Insight.of(emptyList(), listOf(habit("h", kept)), today, zone)
        assertEquals(30, i.habitScheduled)
        assertEquals(15, i.habitCheckIns)
        assertEquals(50, i.habitConsistency)
    }

    @Test
    fun `the week holds seven days ending today`() {
        val i = Insight.of(
            todos = listOf(todo("a", "completed", scheduled = today, completed = today)),
            habits = emptyList(),
            today = today,
            zone = zone
        )
        assertEquals(7, i.week.size)
        assertEquals(today, i.week.last().first)
        assertEquals(today.minusDays(6), i.week.first().first)
        assertEquals(1, i.weekTotal)
    }

    @Test
    fun `on time counts finishing on or before the scheduled day`() {
        val i = Insight.of(
            todos = listOf(
                todo("early", "completed", scheduled = today, completed = today.minusDays(1)),
                todo("onDay", "completed", scheduled = today, completed = today),
                todo("late", "completed", scheduled = today.minusDays(3), completed = today)
            ),
            habits = emptyList(),
            today = today,
            zone = zone
        )
        assertEquals(3, i.completed)
        assertEquals(66, i.onTimePercent)
    }
}
