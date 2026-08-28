package com.nizkarya.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import com.nizkarya.app.logic.DayStreak
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.ui.components.ScreenHeader
import com.nizkarya.app.ui.components.timestampLocalDate
import com.nizkarya.app.ui.theme.BrandCoral
import com.nizkarya.app.ui.theme.BrandViolet
import com.nizkarya.app.ui.theme.Success
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun InsightsScreen(todos: List<Todo>, habits: List<Habit>) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val active = todos.filter { it.archivedAt == null }

    // Last 7 days of completions (by completedDate).
    val week: List<Pair<LocalDate, Int>> = (6 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val count = active.count { todo ->
            todo.status == "completed" &&
                todo.completedDate?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate() == date
        }
        date to count
    }
    val weekTotal = week.sumOf { it.second }
    val bestDay = week.maxByOrNull { it.second }
    val dayStreak = DayStreak.current(active, today)

    // On-time vs spillover among completed (completed on/before its scheduled day).
    val completed = active.filter { it.status == "completed" }
    val onTime = completed.count { todo ->
        val sched = timestampLocalDate(todo.scheduledDate)
        val done = timestampLocalDate(todo.completedDate)
        sched != null && done != null && !done.isAfter(sched)
    }
    val spillover = completed.count { todo ->
        val sched = timestampLocalDate(todo.scheduledDate)
        val done = timestampLocalDate(todo.completedDate)
        sched != null && done != null && done.isAfter(sched)
    }

    // Habit consistency over the last 30 days.
    val activeHabits = habits.filter { it.archivedAt == null }
    var scheduledCount = 0
    var doneCount = 0
    activeHabits.forEach { habit ->
        for (back in 0..29) {
            val date = today.minusDays(back.toLong())
            val created = habit.createdAt?.toDate()?.toInstant()
                ?.atZone(HabitLogic.zoneOf(habit.timezone))?.toLocalDate()
            if (created != null && date < created) continue
            if (!HabitLogic.isScheduledOn(habit, date)) continue
            scheduledCount++
            if (date.toString() in habit.completionDates) doneCount++
        }
    }
    val habitConsistency =
        if (scheduledCount > 0) (doneCount * 100) / scheduledCount else 0
    val bestHabitStreak = activeHabits.maxOfOrNull { HabitLogic.currentStreak(it) } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        ScreenHeader(
            title = "Insights",
            subtitle = "How your week is really going."
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Tasks completed — last 7 days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$weekTotal total" + (
                        bestDay?.takeIf { it.second > 0 }?.let {
                            " · best: ${it.first.format(DateTimeFormatter.ofPattern("EEE"))}" +
                                " (${it.second})"
                        } ?: ""
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                WeekBarChart(week)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InsightStat("🔥 $dayStreak", "Day streak")
                InsightStat("$onTime", "On time")
                InsightStat("$spillover", "Spillover")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Habits — last 30 days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InsightStat("$habitConsistency%", "Consistency")
                    InsightStat("$doneCount/$scheduledCount", "Check-ins")
                    InsightStat("🔥 $bestHabitStreak", "Best streak")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InsightStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeekBarChart(week: List<Pair<LocalDate, Int>>) {
    val maxCount = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val labels = week.map { it.first.format(DateTimeFormatter.ofPattern("EEE")) }
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val barCount = week.size
            if (barCount == 0) return@Canvas
            val gap = size.width * 0.04f
            val barWidth = (size.width - gap * (barCount - 1)) / barCount
            week.forEachIndexed { index, (_, count) ->
                val left = index * (barWidth + gap)
                // Track
                drawRoundRect(
                    color = track,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 4f)
                )
                if (count > 0) {
                    val barHeight = size.height * (count.toFloat() / maxCount)
                    drawRoundRect(
                        color = if (count == maxCount) BrandCoral else BrandViolet,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 4f)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { (_, count) ->
                Text(
                    text = if (count > 0) "$count" else "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (count > 0) Success
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
