package com.nizkarya.app.logic

import com.nizkarya.app.data.Habit
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min

/**
 * Habit scheduling, streaks, and milestones, ported from the web app's
 * src/lib/habitUtils.ts.
 */
object HabitLogic {

    val milestones = listOf(1, 5, 10, 20, 35, 50, 75, 100, 150, 200, 300, 500)

    data class MilestoneProgress(
        val level: Int,
        val currentMilestone: Int,
        val nextMilestone: Int?,
        val progressToNext: Int,
        val completionsNeeded: Int
    )

    fun zoneOf(timezone: String?): ZoneId = try {
        if (timezone.isNullOrBlank()) ZoneId.systemDefault() else ZoneId.of(timezone)
    } catch (e: Exception) {
        ZoneId.systemDefault()
    }

    /** "yyyy-MM-dd", matching the web app's date keys. */
    fun dateKey(date: LocalDate): String = date.toString()

    fun todayKey(habit: Habit): String = LocalDate.now(zoneOf(habit.timezone)).toString()

    fun isDoneToday(habit: Habit): Boolean = todayKey(habit) in habit.completionDates

    /** Sunday = 0 … Saturday = 6, matching the web app. */
    private fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

    private fun clampDay(year: Int, month: Int, day: Int): Int =
        min(day, YearMonth.of(year, month).lengthOfMonth())

    private fun anchorDate(habit: Habit, fallback: LocalDate): LocalDate =
        habit.createdAt?.toDate()?.toInstant()
            ?.atZone(zoneOf(habit.timezone))?.toLocalDate() ?: fallback

    private fun monthIntervalMatch(habit: Habit, date: LocalDate, interval: Int): Boolean {
        val anchor = anchorDate(habit, date)
        val monthsSince = (date.year - anchor.year) * 12 + (date.monthValue - anchor.monthValue)
        return abs(monthsSince) % interval == 0
    }

    fun isScheduledOn(habit: Habit, date: LocalDate): Boolean {
        val days = habit.reminderDays
        return when (habit.frequency) {
            "daily" -> true
            "weekly" -> if (days.isEmpty()) true else dayIndex(date) in days
            "monthly" -> {
                val dayOfMonth = days.firstOrNull() ?: date.dayOfMonth
                date.dayOfMonth == clampDay(date.year, date.monthValue, dayOfMonth)
            }
            "quarterly" -> {
                val dayOfMonth = days.firstOrNull() ?: date.dayOfMonth
                date.dayOfMonth == clampDay(date.year, date.monthValue, dayOfMonth) &&
                    monthIntervalMatch(habit, date, 3)
            }
            "half-yearly" -> {
                val dayOfMonth = days.firstOrNull() ?: date.dayOfMonth
                date.dayOfMonth == clampDay(date.year, date.monthValue, dayOfMonth) &&
                    monthIntervalMatch(habit, date, 6)
            }
            "yearly" -> {
                val month: Int
                val day: Int
                if (days.size >= 2) {
                    month = days[0]
                    day = days[1]
                } else if (days.size == 1) {
                    month = date.monthValue
                    day = days[0]
                } else {
                    month = date.monthValue
                    day = date.dayOfMonth
                }
                if (month < 1 || month > 12) return false
                date.monthValue == month && date.dayOfMonth == clampDay(date.year, month, day)
            }
            else -> true
        }
    }

    fun isScheduledToday(habit: Habit): Boolean =
        isScheduledOn(habit, LocalDate.now(zoneOf(habit.timezone)))

    fun milestoneProgress(totalCompletions: Int): MilestoneProgress {
        val safeTotal = maxOf(totalCompletions, 0)
        val level = milestones.count { safeTotal >= it }
        val currentMilestone = if (level > 0) milestones[level - 1] else 0
        val nextMilestone = milestones.getOrNull(level)
        val progressToNext = if (nextMilestone != null) safeTotal - currentMilestone else 0
        val completionsNeeded =
            if (nextMilestone != null) nextMilestone - currentMilestone else 0
        return MilestoneProgress(
            level, currentMilestone, nextMilestone, progressToNext, completionsNeeded
        )
    }

    /**
     * Consecutive completions over scheduled days walking back from today.
     * Skipped days are neutral; an unfinished *today* doesn't break the streak.
     */
    fun currentStreak(
        habit: Habit,
        today: LocalDate = LocalDate.now(zoneOf(habit.timezone))
    ): Int {
        var streak = 0
        var date = today
        var checked = 0
        while (checked < 366) {
            if (isScheduledOn(habit, date)) {
                val key = date.toString()
                when {
                    key in habit.completionDates -> streak++
                    key in habit.skippedDates -> { /* neutral */ }
                    date == today -> { /* today still pending, doesn't break */ }
                    else -> return streak
                }
            }
            date = date.minusDays(1)
            checked++
        }
        return streak
    }
}
