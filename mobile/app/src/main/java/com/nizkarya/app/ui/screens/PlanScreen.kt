package com.nizkarya.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nizkarya.app.data.FocusBlock
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Subtask
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.QuickAddParser
import com.nizkarya.app.ui.components.EmptyHint
import com.nizkarya.app.ui.components.ChoiceRow
import com.nizkarya.app.ui.components.PriorityDot
import com.nizkarya.app.ui.components.dueMeta
import com.nizkarya.app.ui.components.groupLabel
import com.nizkarya.app.ui.components.timestampLocalDate
import com.nizkarya.app.ui.components.toast
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
    activeFocus: FocusBlock?,
    initialTab: String
) {
    var tab by remember { mutableStateOf(initialTab) }
    val tabs = listOf(
        "todos" to "Todos",
        "habits" to "Habits",
        "focus" to "Focus",
        "review" to "Review"
    )
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0)
        ) {
            tabs.forEach { (value, label) ->
                Tab(
                    selected = tab == value,
                    onClick = { tab = value },
                    text = { Text(label) }
                )
            }
        }
        when (tab) {
            "habits" -> HabitsTab(uid, habits)
            "focus" -> FocusTab(uid, todos, habits, activeFocus)
            "review" -> ReviewTab(uid, todos, habits)
            else -> TodosTab(uid, todos)
        }
    }
}

@Composable
private fun TodosTab(uid: String, todos: List<Todo>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var quickAdd by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Todo?>(null) }

    val today = LocalDate.now()
    val active = todos.filter { it.archivedAt == null }
    val visible = when (filter) {
        "today" -> active.filter {
            it.status == "pending" && timestampLocalDate(it.scheduledDate) == today
        }
        "completed" -> active.filter { it.status == "completed" }
        "flagged" -> active.filter { it.status == "pending" && it.priority == "high" }
        else -> active.filter { it.status == "pending" }
    }

    val groups: List<Pair<String, List<Todo>>> = if (filter == "completed") {
        listOf("Completed" to visible.sortedByDescending { it.completedDate?.seconds ?: 0L })
    } else {
        visible
            .groupBy { timestampLocalDate(it.scheduledDate) }
            .toList()
            .sortedWith(compareBy(nullsLast<LocalDate>()) { it.first })
            .map { (date, items) ->
                groupLabel(date, today) to items.sortedBy { it.scheduledDate?.seconds ?: Long.MAX_VALUE }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = quickAdd,
                onValueChange = { quickAdd = it },
                placeholder = { Text("Quick add — Gym tomorrow at 6pm #health !high") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val input = quickAdd.trim()
                    if (input.isEmpty()) return@IconButton
                    val parsed = QuickAddParser.parse(input)
                    if (parsed.title.isBlank()) {
                        toast(context, "Enter a task description.")
                        return@IconButton
                    }
                    val zone = ZoneId.systemDefault()
                    val scheduled: Timestamp? = when {
                        parsed.date != null -> {
                            val time = parsed.time ?: LocalTime.of(9, 0)
                            Timestamp(Date.from(parsed.date.atTime(time).atZone(zone).toInstant()))
                        }
                        parsed.time != null ->
                            Timestamp(
                                Date.from(today.atTime(parsed.time).atZone(zone).toInstant())
                            )
                        else -> null
                    }
                    scope.launch {
                        try {
                            TodoRepo.add(
                                uid, parsed.title, scheduled, parsed.priority,
                                parsed.tags, emptyList(), "", null, emptyList()
                            )
                            quickAdd = ""
                        } catch (e: Exception) {
                            toast(context, e.message ?: "Unable to add todo")
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add todo")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChoiceRow(
                options = listOf(
                    "all" to "All",
                    "today" to "Today",
                    "completed" to "Done",
                    "flagged" to "Flagged"
                ),
                selected = filter,
                onSelect = { filter = it }
            )
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { editing = null; editorOpen = true }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("New task with details")
        }

        if (groups.isEmpty() || groups.all { it.second.isEmpty() }) {
            EmptyHint("No tasks here. Add one above.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { (label, groupItems) ->
                    item(key = "header-$label") {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(groupItems, key = { it.id }) { todo ->
                        TodoCard(
                            todo = todo,
                            onToggle = {
                                scope.launch {
                                    try {
                                        TodoRepo.toggleStatus(uid, todo)
                                    } catch (e: Exception) {
                                        toast(context, e.message ?: "Unable to update todo")
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    try {
                                        TodoRepo.delete(uid, todo.id)
                                    } catch (e: Exception) {
                                        toast(context, e.message ?: "Unable to delete todo")
                                    }
                                }
                            },
                            onClick = { editing = todo; editorOpen = true }
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (editorOpen) {
        TodoEditorDialog(
            uid = uid,
            existing = editing,
            onDismiss = { editorOpen = false }
        )
    }
}

@Composable
private fun TodoCard(
    todo: Todo,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val (metaLabel, metaColor) = dueMeta(todo)
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggle) {
                if (todo.status == "completed") {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Reopen",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Icon(Icons.Outlined.Circle, contentDescription = "Complete")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (todo.status != "pending") TextDecoration.LineThrough
                    else null
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriorityDot(todo.priority)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = metaLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = metaColor
                    )
                    if (todo.subtasks.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        val done = todo.subtasks.count { it.completed }
                        Text(
                            text = "☑ $done/${todo.subtasks.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (todo.tags.isNotEmpty()) {
                    Text(
                        text = todo.tags.joinToString(" ") { "#$it" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TodoEditorDialog(
    uid: String,
    existing: Todo?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var dateText by remember {
        mutableStateOf(
            timestampLocalDate(existing?.scheduledDate)?.toString()
                ?: LocalDate.now().toString()
        )
    }
    var timeText by remember {
        mutableStateOf(
            existing?.scheduledDate?.toDate()?.toInstant()?.atZone(zone)?.toLocalTime()
                ?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "09:00"
        )
    }
    var priority by remember { mutableStateOf(existing?.priority ?: "medium") }
    var recurrence by remember { mutableStateOf(existing?.recurrence ?: "none") }
    var tagsText by remember { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var contextTagsText by remember {
        mutableStateOf(existing?.contextTags?.joinToString(", ") ?: "")
    }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    val subtasks = remember {
        (existing?.subtasks ?: emptyList()).toMutableStateList()
    }
    var subtaskInput by remember { mutableStateOf("") }

    fun parseList(text: String): List<String> =
        text.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New task" else "Edit task") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Date (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("Time") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    options = listOf("low" to "Low", "medium" to "Med", "high" to "High"),
                    selected = priority,
                    onSelect = { priority = it }
                )
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    options = listOf(
                        "none" to "No",
                        "daily" to "Day",
                        "weekly" to "Week",
                        "monthly" to "Month"
                    ),
                    selected = recurrence,
                    onSelect = { recurrence = it }
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contextTagsText,
                    onValueChange = { contextTagsText = it },
                    label = { Text("Context tags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Subtasks", style = MaterialTheme.typography.labelLarge)
                subtasks.forEachIndexed { index, subtask ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                subtasks[index] =
                                    subtask.copy(completed = !subtask.completed)
                            }
                        ) {
                            if (subtask.completed) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Undo subtask",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            } else {
                                Icon(Icons.Outlined.Circle, contentDescription = "Complete subtask")
                            }
                        }
                        Text(
                            text = subtask.title,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (subtask.completed)
                                TextDecoration.LineThrough else null
                        )
                        IconButton(onClick = { subtasks.removeAt(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove subtask")
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = subtaskInput,
                        onValueChange = { subtaskInput = it },
                        label = { Text("Add a subtask") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val trimmed = subtaskInput.trim()
                            if (trimmed.isNotEmpty()) {
                                subtasks.add(
                                    Subtask(UUID.randomUUID().toString(), trimmed, false)
                                )
                                subtaskInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add subtask")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isEmpty()) {
                        toast(context, "Title is required.")
                        return@Button
                    }
                    val scheduled: Timestamp? = if (dateText.isBlank()) {
                        null
                    } else {
                        try {
                            val date = LocalDate.parse(dateText.trim())
                            val time = if (timeText.isBlank()) LocalTime.of(9, 0)
                            else LocalTime.parse(timeText.trim())
                            Timestamp(Date.from(date.atTime(time).atZone(zone).toInstant()))
                        } catch (e: Exception) {
                            toast(context, "Enter date as yyyy-MM-dd and time as HH:mm.")
                            return@Button
                        }
                    }
                    val rec = if (recurrence == "none") null else recurrence
                    scope.launch {
                        try {
                            if (existing == null) {
                                TodoRepo.add(
                                    uid, cleanTitle, scheduled, priority,
                                    parseList(tagsText), parseList(contextTagsText),
                                    description.trim(), rec, subtasks.toList()
                                )
                            } else {
                                TodoRepo.update(
                                    uid, existing.id, cleanTitle, scheduled, priority,
                                    parseList(tagsText), parseList(contextTagsText),
                                    description.trim(), rec, subtasks.toList()
                                )
                            }
                            onDismiss()
                        } catch (e: Exception) {
                            toast(context, e.message ?: "Unable to save todo")
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
