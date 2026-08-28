package com.nizkarya.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.ui.components.EmptyHint
import com.nizkarya.app.ui.components.formatDue
import com.nizkarya.app.ui.components.timestampLocalDate
import com.nizkarya.app.ui.components.toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private data class MissedHabit(val habit: Habit, val date: LocalDate)

@Composable
fun ReviewTab(uid: String, todos: List<Todo>, habits: List<Habit>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Catch up on what slipped",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        item {
            Text(
                text = "Overdue tasks (${overdue.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (overdue.isEmpty()) {
            item { EmptyHint("Nothing overdue. Nice.") }
        } else {
            item {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                TodoRepo.replanIntoToday(uid, overdue)
                                toast(
                                    context,
                                    "${overdue.size} tasks replanned into today's slots."
                                )
                            } catch (e: Exception) {
                                toast(context, e.message ?: "Unable to replan")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⚡ Replan all ${overdue.size} into today")
                }
            }
        }
        if (overdue.isNotEmpty()) {
            items(overdue, key = { it.id }) { todo ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(todo.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = formatDue(todo.scheduledDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            TodoRepo.rescheduleToToday(uid, todo)
                                        } catch (e: Exception) {
                                            toast(context, e.message ?: "Unable to reschedule")
                                        }
                                    }
                                }
                            ) { Text("Move to today") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            TodoRepo.skip(uid, todo.id)
                                        } catch (e: Exception) {
                                            toast(context, e.message ?: "Unable to skip")
                                        }
                                    }
                                }
                            ) { Text("Skip") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            TodoRepo.archive(uid, todo.id)
                                        } catch (e: Exception) {
                                            toast(context, e.message ?: "Unable to archive")
                                        }
                                    }
                                }
                            ) { Text("Archive") }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Missed habits — last 7 days (${missed.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (missed.isEmpty()) {
            item { EmptyHint("No missed habits this week. 🔥") }
        } else {
            items(missed, key = { it.habit.id + it.date.toString() }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(entry.habit.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = entry.date.format(
                                DateTimeFormatter.ofPattern("EEE, MMM d")
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            HabitRepo.markDoneOn(
                                                uid, entry.habit.id, entry.date.toString()
                                            )
                                        } catch (e: Exception) {
                                            toast(context, e.message ?: "Unable to update")
                                        }
                                    }
                                }
                            ) { Text("Mark done") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            HabitRepo.skipOn(
                                                uid, entry.habit.id, entry.date.toString()
                                            )
                                        } catch (e: Exception) {
                                            toast(context, e.message ?: "Unable to update")
                                        }
                                    }
                                }
                            ) { Text("Skip") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
