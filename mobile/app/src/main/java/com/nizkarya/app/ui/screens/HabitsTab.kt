package com.nizkarya.app.ui.screens

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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.ui.components.ChoiceRow
import com.nizkarya.app.ui.components.EmptyHint
import com.nizkarya.app.ui.components.toast
import kotlinx.coroutines.launch

private fun scheduleSummary(habit: Habit): String {
    val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    return when (habit.frequency) {
        "daily" -> "Every day"
        "weekly" ->
            if (habit.reminderDays.isEmpty() || habit.reminderDays.size == 7) "Every day"
            else habit.reminderDays.sorted()
                .mapNotNull { dayLabels.getOrNull(it) }
                .joinToString(", ")
        "monthly" -> "Day ${habit.reminderDays.firstOrNull() ?: "?"} monthly"
        "yearly" -> {
            val month = habit.reminderDays.getOrNull(0)
            val day = habit.reminderDays.getOrNull(1)
            if (month != null && day != null) "Yearly on $month/$day" else "Yearly"
        }
        else -> habit.frequency.replaceFirstChar { it.uppercase() }
    }
}

@Composable
fun HabitsTab(uid: String, habits: List<Habit>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("active") }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Habit?>(null) }

    val visible = when (filter) {
        "archived" -> habits.filter { it.archivedAt != null }
        "done" -> habits.filter {
            it.archivedAt == null && HabitLogic.isScheduledToday(it) && HabitLogic.isDoneToday(it)
        }
        "pending" -> habits.filter {
            it.archivedAt == null && HabitLogic.isScheduledToday(it) && !HabitLogic.isDoneToday(it)
        }
        else -> habits.filter { it.archivedAt == null }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        ChoiceRow(
            options = listOf(
                "active" to "Active",
                "done" to "Done",
                "pending" to "Pending",
                "archived" to "Archived"
            ),
            selected = filter,
            onSelect = { filter = it }
        )
        TextButton(onClick = { editing = null; editorOpen = true }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("New habit")
        }

        if (visible.isEmpty()) {
            EmptyHint("No habits here yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { habit ->
                    HabitCard(
                        uid = uid,
                        habit = habit,
                        onEdit = { editing = habit; editorOpen = true }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (editorOpen) {
        HabitEditorDialog(uid = uid, existing = editing, onDismiss = { editorOpen = false })
    }
}

@Composable
private fun HabitCard(uid: String, habit: Habit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val done = HabitLogic.isDoneToday(habit)
    val scheduledToday = HabitLogic.isScheduledToday(habit)
    val streak = HabitLogic.currentStreak(habit)
    val progress = HabitLogic.milestoneProgress(habit.completionDates.size)
    val archived = habit.archivedAt != null

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = !archived && scheduledToday,
                onClick = {
                    scope.launch {
                        try {
                            HabitRepo.toggleToday(uid, habit)
                        } catch (e: Exception) {
                            toast(context, e.message ?: "Unable to update habit")
                        }
                    }
                }
            ) {
                if (done) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Undo",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Icon(Icons.Outlined.Circle, contentDescription = "Mark done")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null
                )
                Text(
                    text = scheduleSummary(habit) +
                        (if (habit.habitType == "avoid") " · Avoid" else "") +
                        (if (habit.reminderTime.isNotBlank()) " · ⏰ ${habit.reminderTime}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val milestoneText = if (progress.nextMilestone != null) {
                    "${habit.completionDates.size} done · next milestone ${progress.nextMilestone}"
                } else {
                    "${habit.completionDates.size} done · all milestones!"
                }
                Text(
                    text = (if (streak > 0) "🔥 $streak day streak · " else "") + milestoneText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (archived) {
                IconButton(
                    onClick = {
                        scope.launch {
                            try {
                                HabitRepo.setArchived(uid, habit.id, false)
                            } catch (e: Exception) {
                                toast(context, e.message ?: "Unable to restore habit")
                            }
                        }
                    }
                ) {
                    Icon(Icons.Filled.Unarchive, contentDescription = "Restore")
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            try {
                                HabitRepo.deletePermanently(uid, habit.id)
                            } catch (e: Exception) {
                                toast(context, e.message ?: "Unable to delete habit")
                            }
                        }
                    }
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete permanently")
                }
            } else {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            try {
                                HabitRepo.setArchived(uid, habit.id, true)
                            } catch (e: Exception) {
                                toast(context, e.message ?: "Unable to archive habit")
                            }
                        }
                    }
                ) {
                    Icon(Icons.Filled.Archive, contentDescription = "Archive")
                }
            }
        }
    }
}

@Composable
private fun HabitEditorDialog(uid: String, existing: Habit?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var habitType by remember { mutableStateOf(existing?.habitType ?: "positive") }
    var frequency by remember { mutableStateOf(existing?.frequency ?: "daily") }
    val weeklyDays = remember {
        (existing?.takeIf { it.frequency == "weekly" }?.reminderDays ?: listOf<Int>())
            .toMutableStateList()
    }
    var monthDay by remember {
        mutableStateOf(
            existing?.takeIf { it.frequency == "monthly" }
                ?.reminderDays?.firstOrNull()?.toString() ?: "1"
        )
    }
    var yearMonth by remember {
        mutableStateOf(
            existing?.takeIf { it.frequency == "yearly" }
                ?.reminderDays?.getOrNull(0)?.toString() ?: "1"
        )
    }
    var yearDay by remember {
        mutableStateOf(
            existing?.takeIf { it.frequency == "yearly" }
                ?.reminderDays?.getOrNull(1)?.toString() ?: "1"
        )
    }
    var reminderTime by remember { mutableStateOf(existing?.reminderTime ?: "") }
    var graceText by remember { mutableStateOf((existing?.graceMisses ?: 0).toString()) }
    var contextTagsText by remember {
        mutableStateOf(existing?.contextTags?.joinToString(", ") ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New habit" else "Edit habit") },
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
                Text("Type", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    options = listOf("positive" to "Build", "avoid" to "Avoid"),
                    selected = habitType,
                    onSelect = { habitType = it }
                )
                Text("Frequency", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    options = listOf(
                        "daily" to "Daily",
                        "weekly" to "Weekly",
                        "monthly" to "Monthly",
                        "yearly" to "Yearly"
                    ),
                    selected = frequency,
                    onSelect = { frequency = it }
                )
                when (frequency) {
                    "weekly" -> {
                        Text("Days", style = MaterialTheme.typography.labelLarge)
                        val labels = listOf("S", "M", "T", "W", "T", "F", "S")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            labels.forEachIndexed { index, label ->
                                val selected = index in weeklyDays
                                if (selected) {
                                    Button(
                                        onClick = { weeklyDays.remove(index) },
                                        contentPadding =
                                            androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label) }
                                } else {
                                    OutlinedButton(
                                        onClick = { weeklyDays.add(index) },
                                        contentPadding =
                                            androidx.compose.foundation.layout.PaddingValues(0.dp),
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label) }
                                }
                            }
                        }
                    }
                    "monthly" -> OutlinedTextField(
                        value = monthDay,
                        onValueChange = { monthDay = it },
                        label = { Text("Day of month (1-31)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    "yearly" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = yearMonth,
                            onValueChange = { yearMonth = it },
                            label = { Text("Month (1-12)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = yearDay,
                            onValueChange = { yearDay = it },
                            label = { Text("Day") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {}
                }
                OutlinedTextField(
                    value = reminderTime,
                    onValueChange = { reminderTime = it },
                    label = { Text("Reminder time (HH:mm, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = graceText,
                    onValueChange = { graceText = it },
                    label = { Text("Grace misses") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contextTagsText,
                    onValueChange = { contextTagsText = it },
                    label = { Text("Context tags (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                    if (reminderTime.isNotBlank() &&
                        runCatching { java.time.LocalTime.parse(reminderTime.trim()) }.isFailure
                    ) {
                        toast(context, "Reminder time must be HH:mm.")
                        return@Button
                    }
                    val reminderDays: List<Int> = when (frequency) {
                        "daily" -> listOf(0, 1, 2, 3, 4, 5, 6)
                        "weekly" -> weeklyDays.sorted()
                        "monthly" -> listOf(monthDay.toIntOrNull()?.coerceIn(1, 31) ?: 1)
                        "yearly" -> listOf(
                            yearMonth.toIntOrNull()?.coerceIn(1, 12) ?: 1,
                            yearDay.toIntOrNull()?.coerceIn(1, 31) ?: 1
                        )
                        else -> emptyList()
                    }
                    val contextTags = contextTagsText.split(",")
                        .map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                    scope.launch {
                        try {
                            HabitRepo.save(
                                uid = uid,
                                editingId = existing?.id,
                                title = cleanTitle,
                                habitType = habitType,
                                frequency = frequency,
                                reminderDays = reminderDays,
                                reminderTime = reminderTime.trim(),
                                graceMisses = graceText.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                contextTags = contextTags
                            )
                            onDismiss()
                        } catch (e: Exception) {
                            toast(context, e.message ?: "Unable to save habit")
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
