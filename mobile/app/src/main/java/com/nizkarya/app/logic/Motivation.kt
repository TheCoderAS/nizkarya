package com.nizkarya.app.logic

import java.time.LocalDate

/** One deterministic line per day — no network, no repetition fatigue. */
object Motivation {

    private val lines = listOf(
        "Small steps, done daily, become unstoppable.",
        "Your future self is watching today's choices.",
        "Done beats perfect. Every single time.",
        "One focused hour outworks a distracted day.",
        "Streaks are built one unglamorous day at a time.",
        "You don't need more time — just the next task.",
        "Momentum loves a finished checkbox.",
        "Plan the day, or the day plans you.",
        "The best time to start was earlier. The second best is now.",
        "Discipline is choosing what you want most over what you want now.",
        "Progress hides inside boring consistency.",
        "Win the morning, and the day follows.",
        "Ten minutes of work beats an hour of guilt.",
        "Tick one thing off. Then decide.",
        "Energy follows action, not the other way round."
    )

    fun forDate(date: LocalDate = LocalDate.now()): String =
        lines[(date.dayOfYear + date.year) % lines.size]
}
