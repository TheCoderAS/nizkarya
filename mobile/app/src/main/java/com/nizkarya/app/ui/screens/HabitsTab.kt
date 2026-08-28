@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.ui.components.CheckToggle
import com.nizkarya.app.ui.components.CompactIconButton
import com.nizkarya.app.ui.components.CompactRow
import com.nizkarya.app.ui.components.EditorSheet
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.SegmentedChoice
import com.nizkarya.app.ui.components.streakColor
import com.nizkarya.app.ui.components.TimeField
import com.nizkarya.app.ui.components.notify
import java.time.LocalTime
import kotlinx.coroutines.launch

private val dayInitials = listOf("S", "M", "T", "W", "T", "F", "S")

private fun scheduleSummary(habit: Habit): String = when (habit.frequency) {
    "daily" -> "Every day"
    "weekly" ->
        if (habit.reminderDays.isEmpty() || habit.reminderDays.size == 7) "Every day"
        else habit.reminderDays.sorted().joinToString(", ") { weekdayName(it) }
    "monthly" -> "Day ${habit.reminderDays.firstOrNull() ?: "?"} each month"
    "yearly" -> {
        val m = habit.reminderDays.getOrNull(0)
        val d = habit.reminderDays.getOrNull(1)
        if (m != null && d != null) "Once a year, $d/$m" else "Once a year"
    }
    else -> habit.frequency.replaceFirstChar { it.uppercase() }
}

private fun weekdayName(index: Int): String = listOf(
    "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
).getOrElse(index) { "" }

@Composable
fun HabitsTab(uid: String, habits: List<Habit>) {
    var filter by remember { mutableStateOf("active") }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Habit?>(null) }

    val visible = when (filter) {
        "archived" -> habits.filter { it.archivedAt != null }
        "done" -> habits.filter {
            it.archivedAt == null && HabitLogic.isScheduledToday(it) && HabitLogic.isDoneToday(it)
        }
        "todo" -> habits.filter {
            it.archivedAt == null && HabitLogic.isScheduledToday(it) &&
                !HabitLogic.isDoneToday(it)
        }
        else -> habits.filter { it.archivedAt == null }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; editorOpen = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New habit") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "active" to "All",
                    "todo" to "To do",
                    "done" to "Done",
                    "archived" to "Archived"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { filter = value },
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.SelfImprovement,
                    title = "No habits here",
                    subtitle = "Build a streak — add a habit and check it off daily."
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visible, key = { it.id }) { habit ->
                        HabitRow(
                            uid = uid,
                            habit = habit,
                            onEdit = { editing = habit; editorOpen = true }
                        )
                    }
                }
            }
        }
    }

    if (editorOpen) {
        HabitEditorSheet(uid = uid, existing = editing, onDismiss = { editorOpen = false })
    }
}

@Composable
private fun HabitRow(uid: String, habit: Habit, onEdit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    val done = HabitLogic.isDoneToday(habit)
    val scheduledToday = HabitLogic.isScheduledToday(habit)
    val streak = HabitLogic.currentStreak(habit)
    val progress = HabitLogic.milestoneProgress(habit.completionDates.size)
    val archived = habit.archivedAt != null

    Card(
        onClick = onEdit,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            CompactRow(
                leading = {
                    CheckToggle(
                        checked = done,
                        enabled = !archived && scheduledToday,
                        contentDescription = if (done) "Undo" else "Mark done",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                try {
                                    HabitRepo.toggleToday(uid, habit)
                                } catch (e: Exception) {
                                    notify(scope, snackbar, e.message ?: "Couldn't update habit")
                                }
                            }
                        }
                    )
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (streak > 0) {
                            Text(
                                text = "${streak}d",
                                style = MaterialTheme.typography.labelLarge,
                                color = streakColor()
                            )
                        }
                        Box {
                            CompactIconButton(
                                icon = Icons.Outlined.MoreVert,
                                contentDescription = "More",
                                onClick = { menuOpen = true }
                            )
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                if (archived) {
                                    DropdownMenuItem(
                                        text = { Text("Restore") },
                                        onClick = {
                                            menuOpen = false
                                            scope.launch {
                                                runCatching {
                                                    HabitRepo.setArchived(uid, habit.id, false)
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete forever") },
                                        onClick = {
                                            menuOpen = false
                                            scope.launch {
                                                runCatching {
                                                    HabitRepo.deletePermanently(uid, habit.id)
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = { menuOpen = false; onEdit() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Archive") },
                                        onClick = {
                                            menuOpen = false
                                            scope.launch {
                                                runCatching {
                                                    HabitRepo.setArchived(uid, habit.id, true)
                                                }
                                                notify(scope, snackbar, "Habit archived")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
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
                    text = buildString {
                        append(scheduleSummary(habit))
                        if (habit.habitType == "avoid") append(" · Avoid")
                        if (habit.reminderTime.isNotBlank()) {
                            append(" · ")
                            append(habit.reminderTime)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Milestone progress — a quiet nudge toward the next badge.
            if (!archived && progress.nextMilestone != null) {
                val fraction = if (progress.completionsNeeded > 0) {
                    progress.progressToNext.toFloat() / progress.completionsNeeded.toFloat()
                } else 0f
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${habit.completionDates.size} done · " +
                            "${progress.completionsNeeded - progress.progressToNext} to " +
                            "${progress.nextMilestone}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitEditorSheet(uid: String, existing: Habit?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var habitType by remember { mutableStateOf(existing?.habitType ?: "positive") }
    var frequency by remember { mutableStateOf(existing?.frequency ?: "daily") }
    val weeklyDays = remember {
        (existing?.takeIf { it.frequency == "weekly" }?.reminderDays ?: emptyList())
            .toMutableStateList()
    }
    var monthDay by remember {
        mutableStateOf(
            existing?.takeIf { it.frequency == "monthly" }?.reminderDays?.firstOrNull()
                ?.toString() ?: "1"
        )
    }
    var reminder by remember {
        mutableStateOf(
            existing?.reminderTime?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        )
    }
    var graceText by remember { mutableStateOf((existing?.graceMisses ?: 0).toString()) }

    EditorSheet(
        title = if (existing == null) "New habit" else "Edit habit",
        onDismiss = onDismiss,
        onConfirm = {
            val clean = title.trim()
            if (clean.isEmpty()) {
                notify(scope, snackbar, "Give the habit a name first.")
                return@EditorSheet
            }
            val reminderDays: List<Int> = when (frequency) {
                "daily" -> listOf(0, 1, 2, 3, 4, 5, 6)
                "weekly" -> weeklyDays.sorted()
                "monthly" -> listOf(monthDay.toIntOrNull()?.coerceIn(1, 31) ?: 1)
                else -> emptyList()
            }
            scope.launch {
                try {
                    HabitRepo.save(
                        uid = uid,
                        editingId = existing?.id,
                        title = clean,
                        habitType = habitType,
                        frequency = frequency,
                        reminderDays = reminderDays,
                        reminderTime = reminder?.let {
                            String.format("%02d:%02d", it.hour, it.minute)
                        } ?: "",
                        graceMisses = graceText.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        contextTags = existing?.contextTags ?: emptyList()
                    )
                    onDismiss()
                } catch (e: Exception) {
                    notify(scope, snackbar, e.message ?: "Couldn't save that habit")
                }
            }
        }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(60) },
            label = { Text("Habit name") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        Text("I want to", style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = listOf("positive" to "Build it", "avoid" to "Avoid it"),
            selected = habitType,
            onSelect = { habitType = it }
        )
        Text("How often", style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
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
                Text("On these days", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayInitials.forEachIndexed { index, label ->
                        val selected = index in weeklyDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selected) weeklyDays.remove(index) else weeklyDays.add(index)
                            },
                            label = { Text(label) },
                            modifier = Modifier.size(width = 44.dp, height = 40.dp)
                        )
                    }
                }
            }
            "monthly" -> OutlinedTextField(
                value = monthDay,
                onValueChange = { monthDay = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Day of the month") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            else -> {}
        }
        TimeField(
            value = reminder,
            onValueChange = { reminder = it },
            label = "Remind me at"
        )
        OutlinedTextField(
            value = graceText,
            onValueChange = { graceText = it.filter { c -> c.isDigit() }.take(2) },
            label = { Text("Misses allowed before the streak breaks") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
    }
}
