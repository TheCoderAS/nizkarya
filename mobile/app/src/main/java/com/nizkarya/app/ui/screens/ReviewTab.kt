@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nizkarya.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.ui.components.CompactRow
import com.nizkarya.app.ui.components.PrimaryCta
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.SectionLabel
import com.nizkarya.app.ui.components.formatDue
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.components.timestampLocalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private data class MissedHabit(val habit: Habit, val date: LocalDate)

private val dayChipFormat = DateTimeFormatter.ofPattern("EEE d")

/** Row action sized for Review. The default TextButton is far too generous. */
@Composable
private fun RowAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp, vertical = 0.dp
        ),
        modifier = Modifier.height(30.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ReviewTab(uid: String, todos: List<Todo>, habits: List<Habit>) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val today = LocalDate.now()

    val overdue = todos.filter {
        it.archivedAt == null && it.status == "pending" && it.scheduledDate != null &&
            (timestampLocalDate(it.scheduledDate) ?: today) < today
    }.sortedBy { it.scheduledDate?.seconds ?: 0L }

    val missed: List<MissedHabit> = buildList {
        habits.filter { it.archivedAt == null }.forEach { habit ->
            val created = habit.createdAt?.toDate()?.toInstant()
                ?.atZone(HabitLogic.zoneOf(habit.timezone))?.toLocalDate()
            for (back in 1..7) {
                val date = today.minusDays(back.toLong())
                if (created != null && date < created) continue
                if (!HabitLogic.isScheduledOn(habit, date)) continue
                val key = date.toString()
                if (key in habit.completionDates || key in habit.skippedDates) continue
                add(MissedHabit(habit, date))
            }
        }
    }.sortedByDescending { it.date }

    val missedByHabit: List<Pair<Habit, List<LocalDate>>> = missed
        .groupBy { it.habit.id }
        .values
        .map { entries -> entries.first().habit to entries.map { it.date }.sortedDescending() }
        .sortedByDescending { it.second.size }

    if (overdue.isEmpty() && missed.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.TaskAlt,
            title = "All caught up",
            subtitle = "Nothing overdue and no missed habits this week."
        )
        return
    }

    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (overdue.isNotEmpty()) {
            item { SectionLabel("Overdue · ${overdue.size}") }
            item {
                PrimaryCta(
                    text = "Replan all into today",
                    icon = Icons.Rounded.Bolt,
                    height = 46.dp,
                    onClick = {
                        scope.launch {
                            try {
                                TodoRepo.replanIntoToday(uid, overdue)
                                notify(
                                    scope, snackbar,
                                    "Moved ${overdue.size} tasks into today"
                                )
                            } catch (e: Exception) {
                                notify(scope, snackbar, e.message ?: "Couldn't replan those")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(overdue, key = { it.id }) { todo ->
                Card(shape = MaterialTheme.shapes.medium, colors = cardColors) {
                    // Actions sit inline with the text rather than on their own row:
                    // an overdue list is long by nature and shouldn't need scrolling.
                    CompactRow(
                        trailing = {
                            Row {
                                RowAction("Today") {
                                    scope.launch {
                                        runCatching { TodoRepo.rescheduleToToday(uid, todo) }
                                    }
                                }
                                RowAction("Skip") {
                                    scope.launch { runCatching { TodoRepo.skip(uid, todo.id) } }
                                }
                            }
                        }
                    ) {
                        Text(
                            text = todo.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Was due ${formatDue(todo.scheduledDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (missedByHabit.isNotEmpty()) {
            item { SectionLabel("Missed habits · last 7 days") }
            // One card per habit with its missed days as chips. Listing every
            // (habit, day) pair separately turned one lapsed habit into a wall
            // of near-identical rows.
            items(missedByHabit, key = { it.first.id }) { (habit, dates) ->
                Card(shape = MaterialTheme.shapes.medium, colors = cardColors) {
                    Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 8.dp)) {
                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = "${dates.size} missed · tap a day to mark it done",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dates.forEach { date ->
                                AssistChip(
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                HabitRepo.markDoneOn(
                                                    uid, habit.id, date.toString()
                                                )
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            date.format(dayChipFormat),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    modifier = Modifier.height(30.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
