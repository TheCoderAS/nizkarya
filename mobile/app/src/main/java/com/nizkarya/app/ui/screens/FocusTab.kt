@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.FocusBlock
import com.nizkarya.app.data.FocusRepo
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.notifications.Reminders
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.SectionLabel
import com.nizkarya.app.ui.components.notify
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
    } else block.durationMinutes
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
fun FocusTab(uid: String, todos: List<Todo>, habits: List<Habit>, activeBlock: FocusBlock?) {
    if (activeBlock != null) ActiveFocus(uid, activeBlock, todos, habits)
    else FocusSetup(uid, todos, habits)
}

@Composable
private fun ActiveFocus(uid: String, block: FocusBlock, todos: List<Todo>, habits: List<Habit>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    var nowMs by remember(block.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(block.id) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    DisposableEffect(block.id) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val startMs = block.startedAt?.toDate()?.time ?: nowMs
    val endMs = startMs + block.durationMinutes * 60_000L
    val remainingSec = ((endMs - nowMs) / 1000L).coerceAtLeast(0L)
    val totalSec = (block.durationMinutes * 60L).coerceAtLeast(1L)
    val fraction = 1f - (remainingSec.toFloat() / totalSec.toFloat())

    val selectedTodos = todos.filter { it.id in block.selectedTodoIds }
    val selectedHabits = habits.filter { it.id in block.selectedHabitIds }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (remainingSec > 0) "Focusing" else "Time's up",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Big countdown ring — the centrepiece of the screen.
        Box(modifier = Modifier.size(232.dp), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            val accent = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 30f, cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, style = stroke)
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    style = stroke
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02d:%02d", remainingSec / 60, remainingSec % 60),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${block.durationMinutes} min sprint",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                selectedTodos.forEach { todo ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(todo.title) },
                        leadingContent = {
                            Text(if (todo.status == "completed") "✅" else "•")
                        }
                    )
                }
                selectedHabits.forEach { habit ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(habit.title) },
                        leadingContent = {
                            Text(if (HabitLogic.isDoneToday(habit)) "✅" else "•")
                        }
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
                        notify(scope, snackbar, "Nice work — saved to your insights")
                    } catch (e: Exception) {
                        notify(scope, snackbar, e.message ?: "Couldn't finish the sprint")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Finish sprint") }

        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        FocusRepo.finish(
                            uid, block.id, "cancelled", focusMetrics(block, todos, habits)
                        )
                        Reminders.cancelFocusEnd(context)
                    } catch (e: Exception) {
                        notify(scope, snackbar, e.message ?: "Couldn't cancel the sprint")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Cancel") }
    }
}

@Composable
private fun FocusSetup(uid: String, todos: List<Todo>, habits: List<Habit>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val selectedTodoIds = remember { mutableListOf<String>().toMutableStateList() }
    val selectedHabitIds = remember { mutableListOf<String>().toMutableStateList() }
    var duration by remember { mutableIntStateOf(25) }

    val availableTodos = todos.filter { it.status == "pending" && it.archivedAt == null }
    val availableHabits =
        habits.filter { it.archivedAt == null && HabitLogic.isScheduledToday(it) }
    val nothingToPick = availableTodos.isEmpty() && availableHabits.isEmpty()

    if (nothingToPick) {
        EmptyState(
            icon = Icons.Outlined.Bolt,
            title = "Nothing to focus on",
            subtitle = "Add a task or habit first, then run a timed sprint against it."
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = "Pick what matters, then start the clock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        item { SectionLabel("How long") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 45, 60).forEach { minutes ->
                    FilterChip(
                        selected = duration == minutes,
                        onClick = { duration = minutes },
                        label = { Text("$minutes min") }
                    )
                }
            }
        }
        if (availableTodos.isNotEmpty()) {
            item { SectionLabel("Tasks") }
            items(availableTodos, key = { "t-${it.id}" }) { todo ->
                val checked = todo.id in selectedTodoIds
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(todo.title) },
                    leadingContent = {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                if (it) selectedTodoIds.add(todo.id)
                                else selectedTodoIds.remove(todo.id)
                            }
                        )
                    }
                )
            }
        }
        if (availableHabits.isNotEmpty()) {
            item { SectionLabel("Habits") }
            items(availableHabits, key = { "h-${it.id}" }) { habit ->
                val checked = habit.id in selectedHabitIds
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(habit.title) },
                    leadingContent = {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                if (it) selectedHabitIds.add(habit.id)
                                else selectedHabitIds.remove(habit.id)
                            }
                        )
                    }
                )
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (selectedTodoIds.isEmpty() && selectedHabitIds.isEmpty()) {
                        notify(scope, snackbar, "Pick at least one thing to focus on.")
                        return@Button
                    }
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
                            notify(scope, snackbar, e.message ?: "Couldn't start the sprint")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Start $duration-minute sprint")
            }
        }
    }
}
