@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.nizkarya.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.ui.components.ActionSheet
import com.nizkarya.app.ui.components.CheckToggle
import com.nizkarya.app.ui.components.ConfirmDialog
import com.nizkarya.app.ui.components.GradientFab
import com.nizkarya.app.ui.components.SheetAction
import com.nizkarya.app.ui.components.SwipeableRow
import com.nizkarya.app.ui.components.CompactRow
import com.nizkarya.app.ui.components.EditorSheet
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.SegmentedChoice
import com.nizkarya.app.ui.components.streakColor
import com.nizkarya.app.ui.components.TimeField
import com.nizkarya.app.ui.components.notify
import java.time.LocalDate
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

private enum class DayMark { Done, Skipped, Missed, DueToday, OffDay }

/**
 * Last seven days at a glance. This is the thing a habit tracker exists to
 * show, whether you actually kept it up, and it reads faster than any
 * counter, so it replaces the milestone bar that used to sit here.
 */
@Composable
private fun WeekStrip(habit: Habit, modifier: Modifier = Modifier) {
    val todayLocal = LocalDate.now()
    // The strip is built in the habit's own zone, so "today" for highlighting
    // has to come from that zone too, not the system default.
    val zoneToday = remember(habit.timezone, todayLocal) {
        LocalDate.now(HabitLogic.zoneOf(habit.timezone))
    }
    // Seven scheduling checks per habit; worth doing once, not once a frame.
    val days: List<Pair<LocalDate, DayMark>> = remember(habit, todayLocal) {
        val zone = HabitLogic.zoneOf(habit.timezone)
        val today = LocalDate.now(zone)
        val created = habit.createdAt?.toDate()?.toInstant()?.atZone(zone)?.toLocalDate()
        (6 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val key = date.toString()
            val scheduled = HabitLogic.isScheduledOn(habit, date) &&
                (created == null || !date.isBefore(created))
            date to when {
                key in habit.completionDates -> DayMark.Done
                key in habit.skippedDates -> DayMark.Skipped
                !scheduled -> DayMark.OffDay
                date == today -> DayMark.DueToday
                else -> DayMark.Missed
            }
        }
    }

    val doneColor = MaterialTheme.colorScheme.primary
    val missedColor = MaterialTheme.colorScheme.error
    val mutedColor = MaterialTheme.colorScheme.outlineVariant
    val skippedColor = MaterialTheme.colorScheme.outline

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        days.forEach { (date, mark) ->
            Column(
                modifier = Modifier.width(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .then(
                            when (mark) {
                                DayMark.Done -> Modifier.background(doneColor, CircleShape)
                                DayMark.Skipped ->
                                    Modifier.border(1.5.dp, skippedColor, CircleShape)
                                DayMark.Missed ->
                                    Modifier.border(1.5.dp, missedColor, CircleShape)
                                DayMark.DueToday ->
                                    Modifier.border(1.5.dp, doneColor, CircleShape)
                                DayMark.OffDay -> Modifier.background(mutedColor, CircleShape)
                            }
                        )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dayInitials[date.dayOfWeek.value % 7],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (date == zoneToday) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun HabitsTab(uid: String, habits: List<Habit>) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var filter by remember { mutableStateOf("active") }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var actionsFor by remember { mutableStateOf<Habit?>(null) }
    var deleteAsk by remember { mutableStateOf<Habit?>(null) }

    fun archiveWithUndo(habit: Habit) {
        scope.launch {
            try {
                HabitRepo.setArchived(uid, habit.id, true)
                val result = snackbar.showSnackbar(
                    message = "Habit archived",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    HabitRepo.setArchived(uid, habit.id, false)
                }
            } catch (e: Exception) {
                notify(scope, snackbar, e.message ?: "Couldn't archive that habit")
            }
        }
    }

    // Each of these predicates parses a time zone and builds a LocalDate per
    // habit, so recomputing them on every frame of an animation is real work.
    val today = LocalDate.now()
    val visible = remember(habits, filter, today) {
        when (filter) {
            "archived" -> habits.filter { it.archivedAt != null }
            "done" -> habits.filter {
                it.archivedAt == null && HabitLogic.isScheduledToday(it) &&
                    HabitLogic.isDoneToday(it)
            }
            "todo" -> habits.filter {
                it.archivedAt == null && HabitLogic.isScheduledToday(it) &&
                    !HabitLogic.isDoneToday(it)
            }
            else -> habits.filter { it.archivedAt == null }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            GradientFab(
                text = "New habit",
                icon = Icons.Rounded.Add,
                onClick = { editing = null; editorOpen = true }
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
                    icon = Icons.Rounded.SelfImprovement,
                    title = "No habits here",
                    subtitle = "Add a habit and check it off each day to get a streak going."
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visible, key = { it.id }) { habit ->
                        val archived = habit.archivedAt != null
                        if (archived) {
                            // Swiping an archived habit has no sensible meaning;
                            // long-press offers Restore and Delete forever.
                            HabitRow(
                                uid = uid,
                                habit = habit,
                                onEdit = {},
                                onLongPress = { actionsFor = habit },
                                modifier = Modifier.animateItem()
                            )
                        } else {
                            SwipeableRow(
                                onComplete = {
                                    scope.launch {
                                        runCatching { HabitRepo.toggleToday(uid, habit) }
                                    }
                                },
                                onArchive = { archiveWithUndo(habit) },
                                modifier = Modifier.animateItem()
                            ) {
                                HabitRow(
                                    uid = uid,
                                    habit = habit,
                                    onEdit = { editing = habit; editorOpen = true },
                                    onLongPress = { actionsFor = habit }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    actionsFor?.let { habit ->
        val archived = habit.archivedAt != null
        ActionSheet(
            title = habit.title,
            actions = buildList {
                if (archived) {
                    add(
                        SheetAction(Icons.Rounded.Unarchive, "Restore") {
                            scope.launch {
                                runCatching { HabitRepo.setArchived(uid, habit.id, false) }
                            }
                        }
                    )
                } else {
                    add(
                        SheetAction(Icons.Rounded.Edit, "Edit") {
                            editing = habit
                            editorOpen = true
                        }
                    )
                    add(SheetAction(Icons.Rounded.Archive, "Archive") { archiveWithUndo(habit) })
                }
                add(
                    SheetAction(Icons.Rounded.Delete, "Delete forever", destructive = true) {
                        deleteAsk = habit
                    }
                )
            },
            onDismiss = { actionsFor = null }
        )
    }

    deleteAsk?.let { habit ->
        ConfirmDialog(
            title = "Delete this habit?",
            text = "“${habit.title}” and its whole history will be gone for good.",
            confirmLabel = "Delete forever",
            onConfirm = {
                deleteAsk = null
                scope.launch {
                    runCatching { HabitRepo.deletePermanently(uid, habit.id) }
                }
            },
            onDismiss = { deleteAsk = null }
        )
    }

    if (editorOpen) {
        HabitEditorSheet(uid = uid, existing = editing, onDismiss = { editorOpen = false })
    }
}

@Composable
private fun HabitRow(
    uid: String,
    habit: Habit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current

    // currentStreak walks backwards day by day, so it is not something to redo
    // on every recomposition of a visible row.
    val today = LocalDate.now()
    val archived = habit.archivedAt != null
    val done = remember(habit, today) { HabitLogic.isDoneToday(habit) }
    val scheduledToday = remember(habit, today) { HabitLogic.isScheduledToday(habit) }
    val streak = remember(habit, today) { HabitLogic.currentStreak(habit) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { if (!archived) onEdit() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            )
    ) {
        Column {
            CompactRow(
                leading = {
                    CheckToggle(
                        checked = done,
                        enabled = !archived && scheduledToday,
                        contentDescription = when {
                            done -> "Undo"
                            habit.habitType == "avoid" -> "Avoided it today"
                            else -> "Mark done"
                        },
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
                trailing = if (streak > 0) {
                    {
                        Text(
                            text = "${streak}d",
                            style = MaterialTheme.typography.labelLarge,
                            color = streakColor(),
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                } else {
                    null
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
            if (!archived) {
                WeekStrip(
                    habit = habit,
                    modifier = Modifier.padding(start = 44.dp, end = 12.dp, bottom = 8.dp)
                )
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
    var graceText by remember {
        mutableStateOf((existing?.graceMisses ?: 0).coerceIn(0, 2).toString())
    }

    fun snapshot(): List<Any?> = listOf(
        title, habitType, frequency, weeklyDays.toList(), monthDay, reminder, graceText
    )
    val original = remember { snapshot() }

    EditorSheet(
        title = if (existing == null) "New habit" else "Edit habit",
        dirty = snapshot() != original,
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
        Text("Allow missed days", style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = listOf("0" to "None", "1" to "1 day", "2" to "2 days"),
            selected = graceText,
            onSelect = { graceText = it }
        )
        Text(
            "How many days you can miss before your streak resets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
    }
}
