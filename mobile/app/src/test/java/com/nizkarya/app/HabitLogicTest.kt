package com.nizkarya.app

import com.nizkarya.app.data.Habit
import com.nizkarya.app.logic.HabitLogic
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from the web app's src/lib/habitUtils tests. */
class HabitLogicTest {

    private fun habit(
        frequency: String = "daily",
        reminderDays: List<Int> = emptyList(),
        completionDates: List<String> = emptyList(),
        skippedDates: List<String> = emptyList()
    ) = Habit(
        id = "h1",
        title = "Test",
        habitType = "positive",
        reminderTime = "",
        reminderDays = reminderDays,
        completionDates = completionDates,
        skippedDates = skippedDates,
        timezone = null,
        frequency = frequency,
        graceMisses = 0,
        contextTags = emptyList(),
        triggerAfterHabitId = null,
        createdAt = null,
        archivedAt = null
    )

    // Sunday.
    private val sunday: LocalDate = LocalDate.of(2026, 5, 31)

    @Test
    fun `date key is ISO formatted`() {
        assertEquals("2026-05-31", HabitLogic.dateKey(sunday))
    }

    @Test
    fun `daily habits schedule every day`() {
        assertTrue(HabitLogic.isScheduledOn(habit("daily"), sunday))
    }

    @Test
    fun `weekly habits match selected weekdays`() {
        assertTrue(HabitLogic.isScheduledOn(habit("weekly", listOf(0)), sunday))
        assertFalse(HabitLogic.isScheduledOn(habit("weekly", listOf(1)), sunday))
    }

    @Test
    fun `weekly with no days selected schedules every day`() {
        assertTrue(HabitLogic.isScheduledOn(habit("weekly"), sunday))
    }

    @Test
    fun `monthly habits match configured day with clamping`() {
        assertTrue(HabitLogic.isScheduledOn(habit("monthly", listOf(31)), sunday))
        assertFalse(HabitLogic.isScheduledOn(habit("monthly", listOf(15)), sunday))
        // Feb 28 in a non-leap year clamps day 31 down.
        val feb28 = LocalDate.of(2026, 2, 28)
        assertTrue(HabitLogic.isScheduledOn(habit("monthly", listOf(31)), feb28))
    }

    @Test
    fun `milestone progress at zero`() {
        val progress = HabitLogic.milestoneProgress(0)
        assertEquals(0, progress.level)
        assertEquals(0, progress.currentMilestone)
        assertEquals(1, progress.nextMilestone)
    }

    @Test
    fun `milestone progress between milestones`() {
        val progress = HabitLogic.milestoneProgress(7)
        assertEquals(5, progress.currentMilestone)
        assertEquals(10, progress.nextMilestone)
        assertEquals(2, progress.progressToNext)
        assertEquals(5, progress.completionsNeeded)
    }

    @Test
    fun `milestone progress caps at the top`() {
        val progress = HabitLogic.milestoneProgress(999)
        assertEquals(HabitLogic.milestones.size, progress.level)
        assertEquals(null, progress.nextMilestone)
    }

    @Test
    fun `streak counts consecutive completions and today pending does not break`() {
        val h = habit(
            "daily",
            completionDates = listOf("2026-05-29", "2026-05-30")
        )
        assertEquals(2, HabitLogic.currentStreak(h, sunday))
    }

    @Test
    fun `streak breaks on a missed scheduled day`() {
        val h = habit(
            "daily",
            completionDates = listOf("2026-05-30", "2026-05-27")
        )
        // 05-29 missed → streak is only the 05-30 completion.
        assertEquals(1, HabitLogic.currentStreak(h, sunday))
    }

    @Test
    fun `skipped days are neutral for streaks`() {
        val h = habit(
            "daily",
            completionDates = listOf("2026-05-28", "2026-05-30"),
            skippedDates = listOf("2026-05-29")
        )
        assertEquals(2, HabitLogic.currentStreak(h, sunday))
    }
}
