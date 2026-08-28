package com.nizkarya.app.logic

import java.time.LocalDate
import java.time.LocalTime

/**
 * Natural-language quick-add parser, a 1:1 port of the web app's
 * src/lib/quickAddParser.ts.
 *
 * Supported (all optional, order-independent):
 *   #tag                → tags
 *   !high/!h/!med/!m/!low/!l → priority
 *   "at 6pm", "18:30", "6:30pm" → time
 *   today / tonight / tomorrow / weekday / next weekday → date
 * Everything left over becomes the title.
 */
data class QuickAdd(
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val priority: String,
    val tags: List<String>
)

object QuickAddParser {

    private val priorityWords = mapOf(
        "!high" to "high", "!h" to "high",
        "!medium" to "medium", "!med" to "medium", "!m" to "medium",
        "!low" to "low", "!l" to "low"
    )

    private val weekdays = listOf(
        "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"
    )

    private val tagRegex = Regex("""\s#([\w-]+)""")
    private val priorityRegex = Regex("""\s(![A-Za-z]+)""")
    private val timeRegex = Regex(
        """\s(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b|\s(?:at\s+)?(\d{1,2}):(\d{2})\b""",
        RegexOption.IGNORE_CASE
    )
    private val weekdayRegex = Regex(
        """\b(sunday|monday|tuesday|wednesday|thursday|friday|saturday)\b"""
    )
    private val nextRegex = Regex("""\bnext\s+(\w+)\b""")

    fun parse(input: String, today: LocalDate = LocalDate.now()): QuickAdd {
        var working = " ${input.trim()} "
        val tags = mutableListOf<String>()
        var priority = "medium"
        var time: LocalTime? = null
        var date: LocalDate? = null

        working = tagRegex.replace(working) { match ->
            val tag = match.groupValues[1].lowercase()
            if (tag !in tags) tags.add(tag)
            " "
        }

        working = priorityRegex.replace(working) { match ->
            val mapped = priorityWords[match.groupValues[1].lowercase()]
            if (mapped != null) {
                priority = mapped
                " "
            } else {
                match.value
            }
        }

        val timeMatch = timeRegex.find(working)
        if (timeMatch != null) {
            val g = timeMatch.groupValues
            val hours: Int
            val minutes: Int
            if (g[3].isNotEmpty()) {
                var h = g[1].toInt() % 12
                if (g[3].lowercase() == "pm") h += 12
                hours = h
                minutes = if (g[2].isNotEmpty()) g[2].toInt() else 0
            } else {
                hours = g[4].toInt()
                minutes = g[5].toInt()
            }
            if (hours in 0..23 && minutes in 0..59) {
                time = LocalTime.of(hours, minutes)
                working = working.replaceFirst(timeMatch.value, " ")
            }
        }

        val lower = working.lowercase()
        when {
            Regex("""\btoday\b""").containsMatchIn(lower) -> {
                date = today
                working = Regex("""\btoday\b""", RegexOption.IGNORE_CASE)
                    .replaceFirst(working, " ")
            }
            Regex("""\btonight\b""").containsMatchIn(lower) -> {
                date = today
                if (time == null) time = LocalTime.of(20, 0)
                working = Regex("""\btonight\b""", RegexOption.IGNORE_CASE)
                    .replaceFirst(working, " ")
            }
            Regex("""\btomorrow\b""").containsMatchIn(lower) -> {
                date = today.plusDays(1)
                working = Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE)
                    .replaceFirst(working, " ")
            }
            else -> {
                val nextMatch = nextRegex.find(lower)
                val weekdayMatch = weekdayRegex.find(lower)
                if (nextMatch != null && nextMatch.groupValues[1] in weekdays) {
                    val target = weekdays.indexOf(nextMatch.groupValues[1])
                    date = upcoming(today, target).plusDays(7)
                    working = Regex("""\bnext\s+\w+\b""", RegexOption.IGNORE_CASE)
                        .replaceFirst(working, " ")
                } else if (weekdayMatch != null) {
                    val target = weekdays.indexOf(weekdayMatch.groupValues[1])
                    date = upcoming(today, target)
                    working = Regex(
                        """\b(sunday|monday|tuesday|wednesday|thursday|friday|saturday)\b""",
                        RegexOption.IGNORE_CASE
                    ).replaceFirst(working, " ")
                }
            }
        }

        val title = working.replace(Regex("""\s+"""), " ").trim()
        return QuickAdd(title, date, time, priority, tags)
    }

    /** Sunday = 0 … Saturday = 6, matching the web app. */
    private fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

    /** Upcoming occurrence of the target weekday (today's own weekday → next week). */
    private fun upcoming(now: LocalDate, target: Int): LocalDate {
        var delta = (target - dayIndex(now) + 7) % 7
        if (delta == 0) delta = 7
        return now.plusDays(delta.toLong())
    }
}
