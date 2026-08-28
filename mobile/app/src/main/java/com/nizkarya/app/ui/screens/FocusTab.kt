package com.nizkarya.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.FocusBlock
import com.nizkarya.app.data.FocusRepo
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.notifications.Reminders
import com.nizkarya.app.ui.components.EmptyHint
import com.nizkarya.app.ui.components.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun focusMetrics(
    block: FocusBlock,
    todos: List<Todo>,
    habits: List<Habit>
): Map<String, Any> {
    val selectedTodos = todos.filter { it.id in block.selectedTodoIds }
    val selectedHabits = habits.filter { it.id in block.selectedHabitIds }
    val completedTodos = selectedTodos.count { it.status == "completed" }
    val completedHabits = selectedHabits.count { HabitLogic.isDoneToday(it) }
    val totalItems = selectedTodos.size + selectedHabits.size
    val completionRate =
        if (totalItems > 0) (completedTodos + completedHabits).toDouble() / totalItems else 0.0
    val startedAtMs = block.startedAt?.toDate()?.time
    val actualMinutes = if (startedAtMs != null) {
        (((System.currentTimeMillis() - startedAtMs) / 60000).toInt()).coerceAtLeast(1)
    } else {
        block.durationMinutes
    }
    return mapOf(
        "totalTodos" to selectedTodos.size,
        "completedTodos" to completedTodos,
        "totalHabits" to selectedHabits.size,
        "completedHabits" to completedHabits,
        "completionRate" to completionRate,
        "actualDurationMinutes" to actualMinutes
    )
}

@Composable
fun FocusTab(
    uid: String,
    todos: List<Todo>,
    habits: List<Habit>,
    activeBlock: FocusBlock?
) {
    if (activeBlock != null) {
        ActiveFocus(uid, activeBlock, todos, habits)
    } else {
        FocusSetup(uid, todos, habits)
    }
}

@Composable
private fun ActiveFocus(
    uid: String,
    block: FocusBlock,
    todos: List<Todo>,
    habits: List<Habit>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nowMs by remember(block.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(block.id) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    // Keep the screen awake while a sprint is running.
    DisposableEffect(block.id) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val startMs = block.startedAt?.toDate()?.time ?: nowMs
    val endMs = startMs + block.durationMinutes * 60_000L
    val remainingSec = ((endMs - nowMs) / 1000L).coerceAtLeast(0L)
    val totalSec = (block.durationMinutes * 60L).coerceAtLeast(1L)
    val progress = 1f - (remainingSec.toFloat() / totalSec.toFloat())
    val minutes = remainingSec / 60
    val seconds = remainingSec % 60

    val selectedTodos = todos.filter { it.id in block.selectedTodoIds }
    val selectedHabits = habits.filter { it.id in block.selectedHabitIds }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (remainingSec > 0) "Stay focused" else "Block finished",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format("%02d:%02d", minutes, seconds),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "In this sprint",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                selectedTodos.forEach { todo ->
                    Text(
                        text = (if (todo.status == "completed") "✅ " else "• ") + todo.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                selectedHabits.forEach { habit ->
                    Text(
                        text = (if (HabitLogic.isDoneToday(habit)) "✅ " else "• ") + habit.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (selectedTodos.isEmpty() && selectedHabits.isEmpty()) {
                    Text(
                        text = "No items selected.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    try {
                        FocusRepo.finish(
                            uid, block.id, "completed", focusMetrics(block, todos, habits)
                        )
                        Reminders.cancelFocusEnd(context)
                        toast(context, "Focus block logged.")
                    } catch (e: Exception) {
                        toast(context, e.message ?: "Unable to complete focus block")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Complete & log metrics")
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        FocusRepo.finish(
                            uid, block.id, "cancelled", focusMetrics(block, todos, habits)
                        )
                        Reminders.cancelFocusEnd(context)
                    } catch (e: Exception) {
                        toast(context, e.message ?: "Unable to cancel focus block")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel block")
        }
    }
}

@Composable
private fun FocusSetup(uid: String, todos: List<Todo>, habits: List<Habit>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedTodoIds = remember { mutableListOf<String>().toMutableStateList() }
    val selectedHabitIds = remember { mutableListOf<String>().toMutableStateList() }
    var durationText by remember { mutableStateOf("25") }

    val availableTodos = todos.filter { it.status == "pending" && it.archivedAt == null }
    val availableHabits = habits.filter { it.archivedAt == null && HabitLogic.isScheduledToday(it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "Plan a focused sprint",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "Pick the work that matters and run a timed sprint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Text(
                text = "Tasks",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (availableTodos.isEmpty()) {
            item { EmptyHint("No pending tasks to focus on.") }
        } else {
            items(availableTodos, key = { "t-" + it.id }) { todo ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = todo.id in selectedTodoIds,
                        onCheckedChange = { checked ->
                            if (checked) selectedTodoIds.add(todo.id)
                            else selectedTodoIds.remove(todo.id)
                        }
                    )
                    Text(todo.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text(
                text = "Habits",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (availableHabits.isEmpty()) {
            item { EmptyHint("No habits scheduled today.") }
        } else {
            items(availableHabits, key = { "h-" + it.id }) { habit ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = habit.id in selectedHabitIds,
                        onCheckedChange = { checked ->
                            if (checked) selectedHabitIds.add(habit.id)
                            else selectedHabitIds.remove(habit.id)
                        }
                    )
                    Text(habit.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("Minutes (5–120)") },
                    singleLine = true,
                    modifier = Modifier.width(160.dp)
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (selectedTodoIds.isEmpty() && selectedHabitIds.isEmpty()) {
                            toast(context, "Select at least one task or habit.")
                            return@Button
                        }
                        val duration = (durationText.toIntOrNull() ?: 25).coerceIn(5, 120)
                        scope.launch {
                            try {
                                FocusRepo.start(
                                    uid,
                                    selectedTodoIds.toList(),
                                    selectedHabitIds.toList(),
                                    duration
                                )
                                Reminders.scheduleFocusEnd(
                                    context,
                                    System.currentTimeMillis() + duration * 60_000L
                                )
                            } catch (e: Exception) {
                                toast(context, e.message ?: "Unable to start focus block")
                            }
                        }
                    }
                ) {
                    Text("Start")
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
