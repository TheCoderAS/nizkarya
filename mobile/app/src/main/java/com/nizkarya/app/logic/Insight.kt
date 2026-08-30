package com.nizkarya.app.logic

import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything expensive, derived once.
 *
 * Habits, You and Today each used to work this out for themselves, inside a
 * `remember` that lives and dies with the screen. A tab switch discards the
 * composition, so every one of those walks ran again on the way in: a thirty
 * day scheduling sweep per habit on two of the screens, plus a streak walk
 * that can cover a year, repeated once per visible row on top of the one the
 * summary already did. All of it on the main thread, during the frame the
 * enter animation was trying to draw, which is why switching tabs stuttered
 * and the new screen appeared to pop rather than fade.
 *
 * Computed above the navigation host instead, this runs once per change to
 * the data rather than once per visit, and switching tabs does no work at all.
 */
data class Insight(
    /** Current streak per habit id, so no row has to walk the year itself. */
    val streaks: Map<String, Int>,
    val habitConsistency: Int,
    val habitCheckIns: Int,
    val habitScheduled: Int,
    val bestHabitStreak: Int,
    val habitsDueToday: Int,
    val week: List<Pair<LocalDate, Int>>,
    val weekTotal: Int,
    val onTimePercent: Int,
    val dayStreak: Int,
    val pending: Int,
    val completed: Int,
    val habitCount: Int
) {
    fun streakOf(habitId: String): Int = streaks[habitId] ?: 0

    companion object {

        /** Days of history the consistency figure covers. */
        private const val WINDOW = 30

        val Empty = Insight(
            streaks = emptyMap(),
            habitConsistency = 0,
            habitCheckIns = 0,
            habitScheduled = 0,
            bestHabitStreak = 0,
            habitsDueToday = 0,
            week = emptyList(),
            weekTotal = 0,
            onTimePercent = 0,
            dayStreak = 0,
            pending = 0,
            completed = 0,
            habitCount = 0
        )

        fun of(
            todos: List<Todo>,
            habits: List<Habit>,
            today: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault()
        ): Insight {
            val active = todos.filter { it.archivedAt == null }
            val liveHabits = habits.filter { it.archivedAt == null }

            // One pass over the window for every habit, carrying the streak,
            // the consistency counts and today's due count out together.
            val streaks = HashMap<String, Int>(liveHabits.size)
            var scheduled = 0
            var checkIns = 0
            var best = 0
            var due = 0

            liveHabits.forEach { habit ->
                val habitZone = HabitLogic.zoneOf(habit.timezone)
                val habitToday = LocalDate.now(habitZone)
                val created = habit.createdAt?.toDate()?.toInstant()
                    ?.atZone(habitZone)?.toLocalDate()

                for (back in 0 until WINDOW) {
                    val date = habitToday.minusDays(back.toLong())
                    if (created != null && date < created) continue
                    if (!HabitLogic.isScheduledOn(habit, date)) continue
                    scheduled++
                    if (date.toString() in habit.completionDates) checkIns++
                }

                val streak = HabitLogic.currentStreak(habit, habitToday)
                streaks[habit.id] = streak
                if (streak > best) best = streak

                if (HabitLogic.isScheduledOn(habit, habitToday) &&
                    habitToday.toString() !in habit.completionDates
                ) {
                    due++
                }
            }

            val week = (6 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                date to active.count { todo ->
                    todo.status == "completed" &&
                        todo.completedDate?.toDate()?.toInstant()?.atZone(zone)
                            ?.toLocalDate() == date
                }
            }

            val done = active.filter { it.status == "completed" }
            var onTime = 0
            done.forEach { todo ->
                val scheduledOn = todo.scheduledDate?.toDate()?.toInstant()
                    ?.atZone(zone)?.toLocalDate()
                val completedOn = todo.completedDate?.toDate()?.toInstant()
                    ?.atZone(zone)?.toLocalDate()
                if (scheduledOn != null && completedOn != null &&
                    !completedOn.isAfter(scheduledOn)
                ) {
                    onTime++
                }
            }

            return Insight(
                streaks = streaks,
                habitConsistency = if (scheduled > 0) (checkIns * 100) / scheduled else 0,
                habitCheckIns = checkIns,
                habitScheduled = scheduled,
                bestHabitStreak = best,
                habitsDueToday = due,
                week = week,
                weekTotal = week.sumOf { it.second },
                onTimePercent = if (done.isNotEmpty()) (onTime * 100) / done.size else 0,
                dayStreak = DayStreak.current(active, today),
                pending = active.count { it.status == "pending" },
                completed = done.size,
                habitCount = liveHabits.size
            )
        }
    }
}
