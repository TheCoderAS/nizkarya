@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Subtask
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.ui.components.CheckToggle
import com.nizkarya.app.ui.components.CompactIconButton
import com.nizkarya.app.ui.components.CompactRow
import com.nizkarya.app.ui.components.DateField
import com.nizkarya.app.ui.components.EditorSheet
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.PriorityDot
import com.nizkarya.app.ui.components.SectionLabel
import com.nizkarya.app.ui.components.SegmentedChoice
import com.nizkarya.app.ui.components.TimeField
import com.nizkarya.app.ui.components.dueMeta
import com.nizkarya.app.ui.components.groupLabel
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.components.timestampLocalDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun PlanScreen(
    uid: String,
    todos: List<Todo>,
    habits: List<Habit>,
    initialTab: String
) {
    var tab by remember { mutableStateOf(initialTab) }
    Column(modifier = Modifier.fillMaxSize()) {
        SegmentedChoice(
            options = listOf(
                "todos" to "Tasks",
                "habits" to "Habits",
                "review" to "Review"
            ),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        when (tab) {
            "habits" -> HabitsTab(uid, habits)
            "review" -> ReviewTab(uid, todos, habits)
            else -> TasksTab(uid, todos)
        }
    }
}

@Composable
private fun TasksTab(uid: String, todos: List<Todo>) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    var filter by remember { mutableStateOf("open") }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Todo?>(null) }

    val today = LocalDate.now()
    // Grouping and sorting the whole list on every recomposition showed up as
    // lag while navigating; it only has to change when the data or filter does.
    val groups: List<Pair<String, List<Todo>>> = remember(todos, filter, today) {
        val active = todos.filter { it.archivedAt == null }
        val visible = when (filter) {
            "today" -> active.filter {
                it.status == "pending" && timestampLocalDate(it.scheduledDate) == today
            }
            "done" -> active.filter { it.status == "completed" }
            "flagged" -> active.filter { it.status == "pending" && it.priority == "high" }
            else -> active.filter { it.status == "pending" }
        }
        if (filter == "done") {
            listOf("Completed" to visible.sortedByDescending { it.completedDate?.seconds ?: 0L })
        } else {
            visible
                .groupBy { timestampLocalDate(it.scheduledDate) }
                .toList()
                .sortedWith(compareBy(nullsLast<LocalDate>()) { it.first })
                .map { (date, items) ->
                    groupLabel(date, today) to
                        items.sortedBy { it.scheduledDate?.seconds ?: Long.MAX_VALUE }
                }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; editorOpen = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New task") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "open" to "Open",
                    "today" to "Today",
                    "flagged" to "High",
                    "done" to "Done"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { filter = value },
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            if (groups.isEmpty() || groups.all { it.second.isEmpty() }) {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "Nothing here yet",
                    subtitle = "Tap New task to add one."
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groups.forEach { (label, groupItems) ->
                        item(key = "h-$label") { SectionLabel(label) }
                        items(groupItems, key = { it.id }) { todo ->
                            TaskRow(
                                todo = todo,
                                onToggle = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        try {
                                            TodoRepo.toggleStatus(uid, todo)
                                        } catch (e: Exception) {
                                            notify(
                                                scope, snackbar,
                                                e.message ?: "Couldn't update that task"
                                            )
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        try {
                                            TodoRepo.delete(uid, todo.id)
                                            notify(scope, snackbar, "Task deleted")
                                        } catch (e: Exception) {
                                            notify(
                                                scope, snackbar,
                                                e.message ?: "Couldn't delete that task"
                                            )
                                        }
                                    }
                                },
                                onArchive = {
                                    scope.launch {
                                        try {
                                            TodoRepo.archive(uid, todo.id)
                                            notify(scope, snackbar, "Task archived")
                                        } catch (e: Exception) {
                                            notify(
                                                scope, snackbar,
                                                e.message ?: "Couldn't archive that task"
                                            )
                                        }
                                    }
                                },
                                onEdit = { editing = todo; editorOpen = true },
                                onToggleSubtask = { subtask ->
                                    scope.launch {
                                        try {
                                            TodoRepo.setSubtaskCompleted(
                                                uid, todo, subtask.id, !subtask.completed
                                            )
                                        } catch (e: Exception) {
                                            notify(
                                                scope, snackbar,
                                                e.message ?: "Couldn't update that step"
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        TaskEditorSheet(uid = uid, existing = editing, onDismiss = { editorOpen = false })
    }
}

@Composable
private fun TaskRow(
    todo: Todo,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onEdit: () -> Unit,
    onToggleSubtask: (Subtask) -> Unit
) {
    val (metaLabel, metaColor) = dueMeta(todo)
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    // A finished task has nothing left to schedule, so editing it is
    // meaningless: the row stops being a tap target and the menu offers
    // only what still applies.
    val done = todo.status != "pending"
    val hasSteps = todo.subtasks.isNotEmpty()
    val stepsDone = todo.subtasks.count { it.completed }

    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val body: @Composable () -> Unit = {
        Column {
            CompactRow(
                leading = {
                    CheckToggle(
                        checked = todo.status == "completed",
                        onClick = onToggle,
                        contentDescription = if (done) "Mark as not done" else "Mark as done"
                    )
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Steps are worked from the list, not from inside the editor.
                        if (hasSteps) {
                            CompactIconButton(
                                icon = if (expanded) Icons.Filled.ExpandLess
                                else Icons.Filled.ExpandMore,
                                contentDescription = if (expanded) "Hide steps"
                                else "Show ${todo.subtasks.size} steps",
                                onClick = { expanded = !expanded }
                            )
                        }
                        Box {
                            CompactIconButton(
                                icon = Icons.Outlined.MoreVert,
                                contentDescription = "More actions",
                                onClick = { menuOpen = true }
                            )
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                if (!done) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = { menuOpen = false; onEdit() }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Archive") },
                                    onClick = { menuOpen = false; onArchive() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = { menuOpen = false; onDelete() }
                                )
                            }
                        }
                    }
                }
            ) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriorityDot(todo.priority)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = metaLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = metaColor
                    )
                    if (hasSteps) {
                        Text(
                            text = " · $stepsDone of ${todo.subtasks.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (todo.tags.isNotEmpty()) {
                        Text(
                            text = " · " + todo.tags.joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded && hasSteps) {
                Column(modifier = Modifier.padding(start = 34.dp, end = 10.dp, bottom = 6.dp)) {
                    todo.subtasks.forEach { subtask ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable { onToggleSubtask(subtask) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (subtask.completed) Icons.Filled.CheckCircle
                                else Icons.Outlined.Circle,
                                contentDescription = null,
                                tint = if (subtask.completed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = subtask.title,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (subtask.completed) {
                                    TextDecoration.LineThrough
                                } else {
                                    null
                                },
                                color = if (subtask.completed) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (done) {
        Card(shape = MaterialTheme.shapes.medium, colors = cardColors) { body() }
    } else {
        Card(onClick = onEdit, shape = MaterialTheme.shapes.medium, colors = cardColors) { body() }
    }
}

@Composable
private fun TaskEditorSheet(uid: String, existing: Todo?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val zone = ZoneId.systemDefault()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var date by remember {
        mutableStateOf(timestampLocalDate(existing?.scheduledDate) ?: LocalDate.now())
    }
    var time by remember {
        mutableStateOf(
            existing?.scheduledDate?.toDate()?.toInstant()?.atZone(zone)?.toLocalTime()
                ?: LocalTime.of(9, 0)
        )
    }
    var priority by remember { mutableStateOf(existing?.priority ?: "medium") }
    var recurrence by remember { mutableStateOf(existing?.recurrence ?: "none") }
    var tagsText by remember { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var notes by remember { mutableStateOf(existing?.description ?: "") }
    val subtasks = remember { (existing?.subtasks ?: emptyList()).toMutableStateList() }
    var subtaskInput by remember { mutableStateOf("") }

    fun parseTags(text: String) =
        text.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    // Snapshot of the form as first shown, so backing out can tell an
    // accidental dismissal from one that would throw away real edits.
    fun snapshot(): List<Any?> = listOf(
        title, date, time, priority, recurrence, tagsText, notes, subtasks.toList()
    )
    val original = remember { snapshot() }

    EditorSheet(
        title = if (existing == null) "New task" else "Edit task",
        dirty = snapshot() != original,
        onDismiss = onDismiss,
        onConfirm = {
            val clean = title.trim()
            if (clean.isEmpty()) {
                notify(scope, snackbar, "Give the task a name first.")
                return@EditorSheet
            }
            val scheduled = Timestamp(
                Date.from(date.atTime(time).atZone(zone).toInstant())
            )
            val rec = if (recurrence == "none") null else recurrence
            scope.launch {
                try {
                    if (existing == null) {
                        TodoRepo.add(
                            uid, clean, scheduled, priority, parseTags(tagsText),
                            emptyList(), notes.trim(), rec, subtasks.toList()
                        )
                    } else {
                        TodoRepo.update(
                            uid, existing.id, clean, scheduled, priority,
                            parseTags(tagsText), existing.contextTags, notes.trim(),
                            rec, subtasks.toList()
                        )
                    }
                    onDismiss()
                } catch (e: Exception) {
                    notify(scope, snackbar, e.message ?: "Couldn't save that task")
                }
            }
        }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(80) },
            label = { Text("What needs doing?") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField(
                value = date,
                onValueChange = { date = it ?: LocalDate.now() },
                allowClear = false,
                modifier = Modifier.weight(1.3f)
            )
            TimeField(
                value = time,
                onValueChange = { time = it ?: LocalTime.of(9, 0) },
                allowClear = false,
                modifier = Modifier.weight(1f)
            )
        }
        Text("Priority", style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = listOf("low" to "Low", "medium" to "Medium", "high" to "High"),
            selected = priority,
            onSelect = { priority = it }
        )
        Text("Repeat", style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = listOf(
                "none" to "Never",
                "daily" to "Daily",
                "weekly" to "Weekly",
                "monthly" to "Monthly"
            ),
            selected = recurrence,
            onSelect = { recurrence = it }
        )
        OutlinedTextField(
            value = tagsText,
            onValueChange = { tagsText = it },
            label = { Text("Tags (comma separated)") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            minLines = 2,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Steps", style = MaterialTheme.typography.labelLarge)
        subtasks.forEachIndexed { index, subtask ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckToggle(
                    checked = subtask.completed,
                    contentDescription = null,
                    onClick = {
                        subtasks[index] = subtask.copy(completed = !subtask.completed)
                    }
                )
                Text(
                    text = subtask.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (subtask.completed) TextDecoration.LineThrough else null
                )
                CompactIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = "Remove step",
                    onClick = { subtasks.removeAt(index) }
                )
            }
        }
        // Enter adds the step and keeps the field focused, so a list of steps
        // can be typed straight through without reaching for a button.
        fun commitStep() {
            val trimmed = subtaskInput.trim()
            if (trimmed.isNotEmpty()) {
                subtasks.add(Subtask(UUID.randomUUID().toString(), trimmed, false))
                subtaskInput = ""
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = subtaskInput,
                onValueChange = { subtaskInput = it },
                label = { Text("Add a step") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitStep() }),
                modifier = Modifier.weight(1f)
            )
            CompactIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Add step",
                onClick = { commitStep() }
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
