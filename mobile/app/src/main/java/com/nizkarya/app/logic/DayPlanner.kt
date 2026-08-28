package com.nizkarya.app.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * "Replan my day": distribute a batch of (overdue) tasks into half-hour
 * slots starting from the next half-hour boundary today. If the schedule
 * would spill past 22:00, remaining tasks stack on the 22:00 slot.
 */
object DayPlanner {

    private val dayEnd: LocalTime = LocalTime.of(22, 0)

    fun nextHalfHour(now: LocalDateTime): LocalDateTime {
        val minute = now.minute
        val add = if (minute % 30 == 0) 30 else 30 - (minute % 30)
        return now.plusMinutes(add.toLong()).withSecond(0).withNano(0)
    }

    /** Returns one slot per task, 30 minutes apart, clamped to 22:00 today. */
    fun slots(count: Int, now: LocalDateTime): List<LocalDateTime> {
        val start = nextHalfHour(now)
        val endOfDay = LocalDateTime.of(LocalDate.from(now), dayEnd)
        return (0 until count).map { index ->
            val slot = start.plusMinutes(30L * index)
            if (slot.isAfter(endOfDay)) endOfDay else slot
        }
    }
}
