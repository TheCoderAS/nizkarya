@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.nizkarya.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.logic.Insight
import com.nizkarya.app.logic.UndoWindow
import com.nizkarya.app.ui.components.AccentFab
import com.nizkarya.app.ui.components.ActionSheet
import com.nizkarya.app.ui.components.CheckToggle
import com.nizkarya.app.ui.components.ConfirmDialog
import com.nizkarya.app.ui.components.EditorSheet
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.FieldLabel
import com.nizkarya.app.ui.components.LabelledField
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.ScreenHeader
import com.nizkarya.app.ui.components.SegmentedChoice
import com.nizkarya.app.ui.components.SheetAction
import com.nizkarya.app.ui.components.StatCard
import com.nizkarya.app.ui.components.SwipeableRow
import com.nizkarya.app.ui.components.TimeField
import com.nizkarya.app.ui.components.TimelineDivider
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.theme.Accents
import com.nizkarya.app.ui.theme.accentOf
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
 * Habits, out of the tab it used to be nested inside.
 *
 * The two numbers at the top are the whole point of a habit tracker: how
 * often you actually turn up, and how long the current run is. Everything
 * under them is the detail behind those two figures.
 */
@Composable
fun HabitsScreen(uid: String, habits: List<Habit>, insight: Insight) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val today = LocalDate.now()

    var filter by remember { mutableStateOf("active") }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var actionsFor by remember { mutableStateOf<Habit?>(null) }
    var deleteAsk by remember { mutableStateOf<Habit?>(null) }

    val mint = accentOf(Accents.Habit)
    val amber = accentOf(Accents.Streak)

    val live = remember(habits) { habits.filter { it.archivedAt == null } }

    val groups: List<Pair<String, List<Habit>>> = remember(habits, filter, today) {
        when (filter) {
            "archived" -> {
                val archived = habits.filter { it.archivedAt != null }
                if (archived.isEmpty()) emptyList() else listOf("Archived" to archived)
            }
            "today" -> {
                val due = live.filter { HabitLogic.isScheduledToday(it) }
                if (due.isEmpty()) emptyList() else listOf("Due today" to due)
            }
            else -> live
                .groupBy { it.frequency.replaceFirstChar { c -> c.uppercase() } }
                .toList()
                .sortedBy { frequencyOrder(it.first) }
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = "Habits",
                    subtitle = if (insight.habitsDueToday == 0) {
                        "Nothing left to check off today"
                    } else {
                        "${insight.habitsDueToday} still to check off today"
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatCard(
                        value = "${insight.habitConsistency}%",
                        label = "Last 30 days",
                        tint = mint,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        value = "${insight.bestHabitStreak}",
                        label = "Longest run",
                        tint = amber,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(14.dp))
                SegmentedChoice(
                    options = listOf(
                        "active" to "All",
                        "today" to "Today",
                        "archived" to "Archived"
                    ),
                    selected = filter,
                    onSelect = { filter = it }
                )
            }

            if (groups.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.SelfImprovement,
                        title = "No habits here",
                        subtitle = "Add one and check it off each day to get a run going."
                    )
                }
            }

            groups.forEach { (label, groupHabits) ->
                item(key = "h-$label") { TimelineDivider(label) }
                items(groupHabits, key = { it.id }) { habit ->
                    val archived = habit.archivedAt != null
                    if (archived) {
                        // Swiping an archived habit has no sensible meaning;
                        // long-press offers Restore and Delete forever.
                        HabitRow(
                            uid = uid,
                            habit = habit,
                            streak = insight.streakOf(habit.id),
                            accent = mint,
                            streakAccent = amber,
                            onEdit = {},
                            onLongPress = { actionsFor = habit },
                            modifier = Modifier.animateItem()
                        )
                    } else {
                        val settled = UndoWindow.isHabitSettled(habit, today)
                        SwipeableRow(
                            onComplete = {
                                if (settled) {
                                    notify(scope, snackbar, UndoWindow.MESSAGE)
                                } else {
                                    scope.launch {
                                        runCatching { HabitRepo.toggleToday(uid, habit) }
                                    }
                                }
                            },
                            onArchive = { archiveWithUndo(habit) },
                            completeEnabled = !settled,
                            modifier = Modifier.animateItem()
                        ) {
                            HabitRow(
                                uid = uid,
                                habit = habit,
                                streak = insight.streakOf(habit.id),
                                accent = mint,
                                streakAccent = amber,
                                onEdit = { editing = habit; editorOpen = true },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actionsFor = habit
                                }
                            )
                        }
                    }
                }
            }
        }
        AccentFab(
            icon = Icons.Rounded.Add,
            contentDescription = "New habit",
            onClick = { editing = null; editorOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 88.dp)
        )
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
                scope.launch { runCatching { HabitRepo.deletePermanently(uid, habit.id) } }
            },
            onDismiss = { deleteAsk = null }
        )
    }

    if (editorOpen) {
        HabitEditorSheet(uid = uid, existing = editing, onDismiss = { editorOpen = false })
    }
}

private fun frequencyOrder(label: String): Int = when (label.lowercase()) {
    "daily" -> 0
    "weekly" -> 1
    "monthly" -> 2
    "quarterly" -> 3
    "half-yearly" -> 4
    "yearly" -> 5
    else -> 6
}

@Composable
private fun HabitRow(
    uid: String,
    habit: Habit,
    streak: Int,
    accent: Color,
    streakAccent: Color,
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
    val settled = UndoWindow.isHabitSettled(habit, today)
    val scheduledToday = remember(habit, today) { HabitLogic.isScheduledToday(habit) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                onClick = { if (!archived) onEdit() },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            )
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .width(3.dp)
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (archived) MaterialTheme.colorScheme.outlineVariant else accent)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckToggle(
                    checked = done,
                    enabled = !archived && scheduledToday,
                    settled = settled,
                    contentDescription = when {
                        settled -> "Done, and past its time"
                        done -> "Undo"
                        habit.habitType == "avoid" -> "Avoided it today"
                        else -> "Mark done"
                    },
                    onClick = {
                        if (settled) {
                            notify(scope, snackbar, UndoWindow.MESSAGE)
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                try {
                                    HabitRepo.toggleToday(uid, habit)
                                } catch (e: Exception) {
                                    notify(scope, snackbar, e.message ?: "Couldn't update habit")
                                }
                            }
                        }
                    }
                )
                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
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
                if (streak > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$streak",
                            style = MaterialTheme.typography.titleLarge,
                            color = streakAccent
                        )
                        Text(
                            text = if (streak == 1) "day" else "days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            if (!archived) {
                WeekStrip(
                    habit = habit,
                    accent = accent,
                    modifier = Modifier.padding(start = 10.dp, end = 12.dp, bottom = 10.dp)
                )
            }
        }
    }
}

/**
 * Last seven days at a glance. This is the thing a habit tracker exists to
 * show, whether you actually kept it up, and it reads faster than any counter.
 */
@Composable
private fun WeekStrip(habit: Habit, accent: Color, modifier: Modifier = Modifier) {
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

    val missed = accentOf(Accents.Late)
    val scheme = MaterialTheme.colorScheme

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        days.forEach { (date, mark) ->
            // Filled tiles rather than dots: seven of them make a bar you can
            // read in one look, which a row of outlines never does.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .then(
                        when (mark) {
                            DayMark.Done -> Modifier.background(accent.copy(alpha = 0.22f))
                            DayMark.Missed -> Modifier.background(missed.copy(alpha = 0.16f))
                            DayMark.Skipped -> Modifier.background(scheme.surfaceContainerHigh)
                            DayMark.DueToday -> Modifier
                                .background(scheme.surfaceContainerHigh)
                                .border(1.5.dp, accent, RoundedCornerShape(7.dp))
                            DayMark.OffDay -> Modifier.background(scheme.surfaceContainerHigh)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayInitials[date.dayOfWeek.value % 7],
                    style = MaterialTheme.typography.labelSmall,
                    color = when (mark) {
                        DayMark.Done -> accent
                        DayMark.Missed -> missed
                        else -> if (date == zoneToday) scheme.onSurface else scheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun HabitEditorSheet(uid: String, existing: Habit?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val accent = accentOf(Accents.Habit)

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
        LabelledField(
            label = "Habit name",
            value = title,
            onValueChange = { title = it.take(60) },
            placeholder = "Morning walk",
            minHeight = 50.dp
        )
        FieldLabel("I want to")
        SegmentedChoice(
            options = listOf("positive" to "Build it", "avoid" to "Avoid it"),
            selected = habitType,
            onSelect = { habitType = it }
        )
        FieldLabel("How often")
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
                FieldLabel("On these days")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayInitials.forEachIndexed { index, label ->
                        val selected = index in weeklyDays
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.background(accent.copy(alpha = 0.22f))
                                            .border(1.dp, accent, CircleShape)
                                    } else {
                                        Modifier.background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    }
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (selected) weeklyDays.remove(index)
                                        else weeklyDays.add(index)
                                    },
                                    onLongClick = {}
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) accent
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            "monthly" -> LabelledField(
                label = "Day of the month",
                value = monthDay,
                onValueChange = { monthDay = it.filter { c -> c.isDigit() }.take(2) },
                placeholder = "1",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            else -> {}
        }
        TimeField(
            value = reminder,
            onValueChange = { reminder = it },
            label = "Remind me at"
        )
        FieldLabel("Allow missed days")
        SegmentedChoice(
            options = listOf("0" to "None", "1" to "1 day", "2" to "2 days"),
            selected = graceText,
            onSelect = { graceText = it }
        )
        Text(
            "How many days you can miss before your run resets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
    }
}
