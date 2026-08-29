@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.CalendarLoad
import com.nizkarya.app.logic.DayLoad
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.ui.components.CheckToggle
import com.nizkarya.app.ui.components.GhostButton
import com.nizkarya.app.ui.components.CompactIconButton
import com.nizkarya.app.ui.components.CompactRow
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.PriorityDot
import com.nizkarya.app.ui.components.SectionLabel
import com.nizkarya.app.ui.components.formatClock
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.components.timestampLocalDate
import com.nizkarya.app.ui.theme.ctaGradient
import com.nizkarya.app.ui.theme.onCta
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val monthLabel = DateTimeFormatter.ofPattern("MMMM yyyy")
private val dayLabel = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val weekdayInitials = listOf("S", "M", "T", "W", "T", "F", "S")

/** Sunday-first column index, matching the weekday initials above. */
private fun LocalDate.columnIndex(): Int = dayOfWeek.value % 7

@Composable
fun CalendarScreen(
    uid: String,
    todos: List<Todo>,
    habits: List<Habit>,
    initialDate: LocalDate
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val today = LocalDate.now()

    var selected by remember { mutableStateOf(initialDate) }
    var month by remember { mutableStateOf(YearMonth.from(initialDate)) }

    // The grid always shows whole weeks, so it starts on the Sunday on or
    // before the first of the month and runs to the Saturday on or after
    // the last. Load for every one of those cells is one pass.
    val gridStart = remember(month) {
        month.atDay(1).minusDays(month.atDay(1).columnIndex().toLong())
    }
    val cellCount = remember(month, gridStart) {
        val used = month.atDay(1).columnIndex() + month.lengthOfMonth()
        ((used + 6) / 7) * 7
    }
    val load = remember(todos, habits, gridStart, cellCount) {
        CalendarLoad.forRange(todos, habits, gridStart, cellCount)
    }

    val dayTasks = remember(todos, selected) {
        todos
            .filter { it.archivedAt == null && timestampLocalDate(it.scheduledDate) == selected }
            .sortedBy { it.scheduledDate?.seconds ?: 0L }
    }
    val dayHabits = remember(habits, selected) {
        habits.filter { it.archivedAt == null && HabitLogic.isScheduledOn(it, selected) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = month.atDay(1).format(monthLabel),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (YearMonth.from(today) != month) {
                    GhostButton(
                        text = "Today",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { month = YearMonth.from(today); selected = today }
                    )
                }
                CompactIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    onClick = { month = month.minusMonths(1) }
                )
                CompactIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Next month",
                    onClick = { month = month.plusMonths(1) }
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayInitials.forEach { initial ->
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in 0 until cellCount / 7) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (column in 0 until 7) {
                            val date = gridStart.plusDays((row * 7 + column).toLong())
                            DayCell(
                                date = date,
                                load = load[date],
                                inMonth = YearMonth.from(date) == month,
                                isToday = date == today,
                                isSelected = date == selected,
                                onClick = { selected = date },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item { SectionLabel(selected.format(dayLabel)) }

        if (dayTasks.isEmpty() && dayHabits.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.EventAvailable,
                    title = "Nothing on this day",
                    subtitle = "Pick another day, or add something from the Plan tab."
                )
            }
        }

        items(dayTasks, key = { "t-" + it.id }) { todo ->
            val done = todo.status == "completed"
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                CompactRow(
                    leading = {
                        CheckToggle(
                            checked = done,
                            contentDescription = if (done) "Mark as not done" else "Mark as done",
                            onClick = {
                                scope.launch {
                                    try {
                                        TodoRepo.toggleStatus(uid, todo)
                                    } catch (e: Exception) {
                                        notify(scope, snackbar, e.message ?: "Couldn't update that")
                                    }
                                }
                            }
                        )
                    }
                ) {
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PriorityDot(todo.priority)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = formatClock(todo.scheduledDate) ?: "No time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(dayHabits, key = { "h-" + it.id }) { habit ->
            val key = selected.toString()
            val done = key in habit.completionDates
            // A habit cannot be completed before the day arrives, and only
            // today can be un-ticked, so future days are read-only here.
            val future = selected.isAfter(today)
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                CompactRow(
                    leading = {
                        CheckToggle(
                            checked = done,
                            enabled = !future && !(done && selected != today),
                            contentDescription = if (done) "Done" else "Mark done",
                            onClick = {
                                scope.launch {
                                    try {
                                        if (selected == today) {
                                            HabitRepo.toggleToday(uid, habit)
                                        } else {
                                            HabitRepo.markDoneOn(uid, habit.id, key)
                                        }
                                    } catch (e: Exception) {
                                        notify(scope, snackbar, e.message ?: "Couldn't update that")
                                    }
                                }
                            }
                        )
                    }
                ) {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (habit.reminderTime.isNotBlank()) {
                            "Habit at ${habit.reminderTime}"
                        } else {
                            "Habit"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    load: DayLoad?,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val numberColor = when {
        isSelected -> onCta()
        !inMonth -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> scheme.onSurface
    }

    Box(modifier = modifier.height(44.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .then(
                    when {
                        isSelected -> Modifier.background(ctaGradient())
                        isToday -> Modifier.border(1.5.dp, scheme.primary, CircleShape)
                        else -> Modifier
                    }
                )
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = numberColor
            )
            Spacer(Modifier.height(2.dp))
            // One dot rather than a count: at this size a number under a
            // number is unreadable, and "is there anything, is it finished"
            // is the question a month grid should answer.
            Box(
                modifier = Modifier.size(4.dp).then(
                    when {
                        load == null || load.total == 0 -> Modifier
                        isSelected -> Modifier.background(onCta(), CircleShape)
                        load.allDone -> Modifier.background(scheme.primary, CircleShape)
                        else -> Modifier.background(scheme.onSurfaceVariant, CircleShape)
                    }
                )
            )
        }
    }
}
