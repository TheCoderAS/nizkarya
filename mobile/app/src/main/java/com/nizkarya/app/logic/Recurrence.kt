package com.nizkarya.app.logic

import java.time.Instant
import java.time.ZoneId

/** Next occurrence for recurring todos — mirrors the web app's behavior. */
object Recurrence {
    fun next(
        from: Instant,
        recurrence: String,
        zone: ZoneId = ZoneId.systemDefault()
    ): Instant {
        val zoned = from.atZone(zone)
        val nextZoned = when (recurrence) {
            "daily" -> zoned.plusDays(1)
            "weekly" -> zoned.plusWeeks(1)
            "monthly" -> zoned.plusMonths(1)
            else -> zoned
        }
        return nextZoned.toInstant()
    }
}
