@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.nizkarya.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.Routine
import com.nizkarya.app.data.RoutineItem
import com.nizkarya.app.data.RoutineRepo
import com.nizkarya.app.data.Subtask
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.CalendarLoad
import com.nizkarya.app.logic.DayLoad
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.ui.components.AccentFab
import com.nizkarya.app.ui.components.ActionSheet
import com.nizkarya.app.ui.components.CheckToggle
import com.nizkarya.app.ui.components.CompactIconButton
import com.nizkarya.app.ui.components.ConfirmDialog
import com.nizkarya.app.ui.components.DateField
import com.nizkarya.app.ui.components.EditorSheet
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.ScreenHeader
import com.nizkarya.app.ui.components.SecondaryButton
import com.nizkarya.app.ui.components.SegmentedChoice
import com.nizkarya.app.ui.components.SheetAction
import com.nizkarya.app.ui.components.SwipeableRow
import com.nizkarya.app.ui.components.TimeField
import com.nizkarya.app.ui.components.TimelineDivider
import com.nizkarya.app.ui.components.dueMeta
import com.nizkarya.app.ui.components.groupLabel
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.components.timestampLocalDate
import com.nizkarya.app.ui.theme.Accents
import com.nizkarya.app.ui.theme.accentOf
import com.nizkarya.app.ui.theme.ctaGradient
import com.nizkarya.app.ui.theme.onCta
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch

private val weekInitials = listOf("S", "M", "T", "W", "T", "F", "S")
private val dayHeading = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** Sunday-first column index, matching [weekInitials]. */
private fun LocalDate.columnIndex(): Int = dayOfWeek.value % 7

/**
 * Everything scheduled, in one place.
 *
 * This absorbs two former destinations. The calendar is now the strip at the
 * top, which opens to a month when you want one, and routines are the run
 * strip under it. Neither was worth a tab of its own: a routine is a way of
 * making tasks, and a calendar is a way of choosing which tasks to look at.
 */
@Composable
fun TasksScreen(
    uid: String,
    todos: List<Todo>,
    habits: List<Habit>,
    routines: List<Routine>
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val today = LocalDate.now()

    var filter by remember { mutableStateOf("open") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var monthOpen by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Todo?>(null) }
    var actionsFor by remember { mutableStateOf<Todo?>(null) }
    var deleteAsk by remember { mutableStateOf<Todo?>(null) }
    var routineEditorOpen by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<Routine?>(null) }
    var routineActions by remember { mutableStateOf<Routine?>(null) }
    var routineDeleteAsk by remember { mutableStateOf<Routine?>(null) }

    val taskAccent = accentOf(Accents.Task)
    val habitAccent = accentOf(Accents.Habit)
    val lateAccent = accentOf(Accents.Late)

    // The strip runs from the Sunday of the current week, so the columns line
    // up with the month grid underneath when it opens.
    val weekStart = remember(today) { today.minusDays(today.columnIndex().toLong()) }
    val gridStart = remember(month) {
        month.atDay(1).minusDays(month.atDay(1).columnIndex().toLong())
    }
    val cellCount = remember(month) {
        val used = month.atDay(1).columnIndex() + month.lengthOfMonth()
        ((used + 6) / 7) * 7
    }
    val load = remember(todos, habits, monthOpen, gridStart, cellCount, weekStart) {
        if (monthOpen) {
            CalendarLoad.forRange(todos, habits, gridStart, cellCount)
        } else {
            CalendarLoad.forRange(todos, habits, weekStart, 7)
        }
    }

    // Grouping and sorting the whole list on every recomposition showed up as
    // lag while navigating; it only has to change when the data does.
    val groups: List<Pair<String, List<Todo>>> = remember(todos, filter, selectedDate, today) {
        val active = todos.filter { it.archivedAt == null }
        val pick = selectedDate
        if (pick != null) {
            val onDay = active
                .filter { timestampLocalDate(it.scheduledDate) == pick }
                .sortedBy { it.scheduledDate?.seconds ?: Long.MAX_VALUE }
            return@remember if (onDay.isEmpty()) {
                emptyList()
            } else {
                listOf("" to onDay)
            }
        }
        val visible = when (filter) {
            "high" -> active.filter { it.status == "pending" && it.priority == "high" }
            "done" -> active.filter { it.status == "completed" }
            else -> active.filter { it.status == "pending" }
        }
        if (filter == "done") {
            listOf("Recently done" to visible.sortedByDescending { it.completedDate?.seconds ?: 0L })
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

    val dayHabits: List<Habit> = remember(habits, selectedDate) {
        val pick = selectedDate
        if (pick == null) {
            emptyList()
        } else {
            habits.filter { it.archivedAt == null && HabitLogic.isScheduledOn(it, pick) }
        }
    }

    fun toggle(todo: Todo) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            try {
                TodoRepo.toggleStatus(uid, todo)
            } catch (e: Exception) {
                notify(scope, snackbar, e.message ?: "Couldn't update that task")
            }
        }
    }

    fun archiveWithUndo(todo: Todo) {
        scope.launch {
            try {
                TodoRepo.archive(uid, todo.id)
                val result = snackbar.showSnackbar(
                    message = "Task archived",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) TodoRepo.unarchive(uid, todo.id)
            } catch (e: Exception) {
                notify(scope, snackbar, e.message ?: "Couldn't archive that task")
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
                    title = "Tasks",
                    subtitle = selectedDate?.format(dayHeading)
                        ?: "Everything you have scheduled"
                )
            }

            item(key = "calendar") {
                if (monthOpen) {
                    MonthGrid(
                        month = month,
                        gridStart = gridStart,
                        cellCount = cellCount,
                        load = load,
                        today = today,
                        selected = selectedDate,
                        accent = taskAccent,
                        onPrevious = { month = month.minusMonths(1) },
                        onNext = { month = month.plusMonths(1) },
                        onSelect = { date ->
                            selectedDate = if (selectedDate == date) null else date
                        }
                    )
                } else {
                    WeekStrip(
                        start = weekStart,
                        load = load,
                        today = today,
                        selected = selectedDate,
                        onSelect = { date ->
                            selectedDate = if (selectedDate == date) null else date
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (monthOpen) "Show the week" else "Show the month",
                        style = MaterialTheme.typography.labelLarge,
                        color = taskAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { monthOpen = !monthOpen }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    if (selectedDate != null) {
                        Text(
                            text = "Clear the day",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { selectedDate = null }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (routines.isNotEmpty() || selectedDate == null) {
                item(key = "routines") {
                    RoutineStrip(
                        routines = routines,
                        accent = taskAccent,
                        onRun = { routine ->
                            scope.launch {
                                try {
                                    RoutineRepo.run(uid, routine)
                                    notify(
                                        scope, snackbar,
                                        "${routine.items.size} tasks added to today"
                                    )
                                } catch (e: Exception) {
                                    notify(scope, snackbar, e.message ?: "Couldn't start that")
                                }
                            }
                        },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            routineActions = it
                        },
                        onNew = { editingRoutine = null; routineEditorOpen = true }
                    )
                }
            }

            if (selectedDate == null) {
                item(key = "filters") {
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(
                        options = listOf(
                            "open" to "Open",
                            "high" to "High",
                            "done" to "Done"
                        ),
                        selected = filter,
                        onSelect = { filter = it }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (groups.isEmpty() && dayHabits.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Inbox,
                        title = if (selectedDate != null) "Nothing on this day" else "Nothing here yet",
                        subtitle = "Use the button below to add a task."
                    )
                }
            }

            groups.forEach { (label, groupItems) ->
                if (label.isNotEmpty()) {
                    item(key = "h-$label") { TimelineDivider(label) }
                }
                items(groupItems, key = { it.id }) { todo ->
                    val overdue = todo.status == "pending" &&
                        (timestampLocalDate(todo.scheduledDate) ?: today) < today
                    SwipeableRow(
                        onComplete = { toggle(todo) },
                        onArchive = { archiveWithUndo(todo) },
                        modifier = Modifier.animateItem()
                    ) {
                        TaskRow(
                            todo = todo,
                            accent = if (overdue) lateAccent else taskAccent,
                            onToggle = { toggle(todo) },
                            onEdit = { editing = todo; editorOpen = true },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                actionsFor = todo
                            },
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

            if (dayHabits.isNotEmpty()) {
                item(key = "day-habits") { TimelineDivider("Habits on this day") }
                items(dayHabits, key = { "dh-" + it.id }) { habit ->
                    val pick = selectedDate ?: today
                    DayHabitRow(uid = uid, habit = habit, date = pick, today = today, accent = habitAccent)
                }
            }
        }
        AccentFab(
            icon = Icons.Rounded.Add,
            contentDescription = "New task",
            onClick = { editing = null; editorOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 88.dp)
        )
    }

    actionsFor?.let { todo ->
        val pending = todo.status == "pending"
        val notToday = timestampLocalDate(todo.scheduledDate) != today
        ActionSheet(
            title = todo.title,
            actions = buildList {
                if (pending) {
                    add(
                        SheetAction(Icons.Rounded.Edit, "Edit") {
                            editing = todo
                            editorOpen = true
                        }
                    )
                    if (notToday) {
                        add(
                            SheetAction(Icons.Rounded.Today, "Do today") {
                                scope.launch {
                                    runCatching { TodoRepo.rescheduleToToday(uid, todo) }
                                }
                            }
                        )
                    }
                }
                add(SheetAction(Icons.Rounded.Archive, "Archive") { archiveWithUndo(todo) })
                add(
                    SheetAction(Icons.Rounded.Delete, "Delete", destructive = true) {
                        deleteAsk = todo
                    }
                )
            },
            onDismiss = { actionsFor = null }
        )
    }

    routineActions?.let { routine ->
        ActionSheet(
            title = routine.title,
            actions = listOf(
                SheetAction(Icons.Rounded.Edit, "Edit") {
                    editingRoutine = routine
                    routineEditorOpen = true
                },
                SheetAction(Icons.Rounded.Delete, "Delete", destructive = true) {
                    routineDeleteAsk = routine
                }
            ),
            onDismiss = { routineActions = null }
        )
    }

    deleteAsk?.let { todo ->
        ConfirmDialog(
            title = "Delete this task?",
            text = "“${todo.title}” will be gone for good.",
            confirmLabel = "Delete",
            onConfirm = {
                deleteAsk = null
                scope.launch {
                    try {
                        TodoRepo.delete(uid, todo.id)
                        notify(scope, snackbar, "Task deleted")
                    } catch (e: Exception) {
                        notify(scope, snackbar, e.message ?: "Couldn't delete that task")
                    }
                }
            },
            onDismiss = { deleteAsk = null }
        )
    }

    routineDeleteAsk?.let { routine ->
        ConfirmDialog(
            title = "Delete this routine?",
            text = "“${routine.title}” will be gone for good. Tasks it already added stay.",
            confirmLabel = "Delete",
            onConfirm = {
                routineDeleteAsk = null
                scope.launch { runCatching { RoutineRepo.delete(uid, routine.id) } }
            },
            onDismiss = { routineDeleteAsk = null }
        )
    }

    if (editorOpen) {
        TaskEditorSheet(
            uid = uid,
            existing = editing,
            defaultDate = selectedDate ?: today,
            onDismiss = { editorOpen = false }
        )
    }

    if (routineEditorOpen) {
        RoutineEditorSheet(
            uid = uid,
            existing = editingRoutine,
            onDismiss = { routineEditorOpen = false }
        )
    }
}

// ── The calendar, folded in ──────────────────────────────────────────────────

@Composable
private fun WeekStrip(
    start: LocalDate,
    load: Map<LocalDate, DayLoad>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (0 until 7).forEach { offset ->
            val date = start.plusDays(offset.toLong())
            DayColumn(
                date = date,
                day = load[date],
                isToday = date == today,
                isSelected = date == selected,
                onClick = { onSelect(date) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    day: DayLoad?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val fraction = if (day == null || day.total == 0) 0f else day.done.toFloat() / day.total
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isSelected) {
                    Modifier.background(ctaGradient())
                } else {
                    Modifier.background(scheme.surfaceContainerLow)
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = weekInitials[date.columnIndex()],
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) onCta().copy(alpha = 0.75f) else scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = when {
                isSelected -> onCta()
                isToday -> MaterialTheme.colorScheme.primary
                else -> scheme.onSurface
            }
        )
        Spacer(Modifier.height(6.dp))
        // A load bar rather than a dot: it says how full the day is and how
        // much of it is finished, in three pixels.
        Box(
            modifier = Modifier
                .padding(horizontal = 7.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isSelected) onCta().copy(alpha = 0.3f) else scheme.surfaceContainerHighest
                )
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) onCta() else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    gridStart: LocalDate,
    cellCount: Int,
    load: Map<LocalDate, DayLoad>,
    today: LocalDate,
    selected: LocalDate?,
    accent: Color,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = month.month.getDisplayName(
                    java.time.format.TextStyle.FULL,
                    java.util.Locale.getDefault()
                ) + " " + month.year,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            CompactIconButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Previous month",
                onClick = onPrevious
            )
            CompactIconButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Next month",
                onClick = onNext
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            weekInitials.forEach { initial ->
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        for (row in 0 until cellCount / 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val date = gridStart.plusDays((row * 7 + column).toLong())
                    val day = load[date]
                    val inMonth = YearMonth.from(date) == month
                    val isSelected = date == selected
                    Box(
                        modifier = Modifier.weight(1f).height(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .then(
                                    when {
                                        isSelected -> Modifier.background(ctaGradient())
                                        date == today -> Modifier.background(
                                            accent.copy(alpha = 0.16f)
                                        )
                                        else -> Modifier
                                    }
                                )
                                .clickable { onSelect(date) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = when {
                                    isSelected -> onCta()
                                    date == today -> accent
                                    !inMonth -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> scheme.onSurface
                                }
                            )
                            Spacer(Modifier.height(2.dp))
                            Box(
                                modifier = Modifier.size(4.dp).then(
                                    when {
                                        day == null || day.total == 0 -> Modifier
                                        isSelected -> Modifier.background(onCta(), CircleShape)
                                        day.allDone -> Modifier.background(
                                            accentOf(Accents.Habit), CircleShape
                                        )
                                        else -> Modifier.background(accent, CircleShape)
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Routines, folded in ──────────────────────────────────────────────────────

@Composable
private fun RoutineStrip(
    routines: List<Routine>,
    accent: Color,
    onRun: (Routine) -> Unit,
    onLongPress: (Routine) -> Unit,
    onNew: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(routines, key = { it.id }) { routine ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .combinedClickable(
                        onClick = { onRun(routine) },
                        onLongClick = { onLongPress(routine) }
                    )
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(routine.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "${routine.items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "new-routine") {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(onClick = onNew)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (routines.isEmpty()) "New routine" else "New",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ── Rows ─────────────────────────────────────────────────────────────────────

@Composable
private fun TaskRow(
    todo: Todo,
    accent: Color,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSubtask: (Subtask) -> Unit
) {
    val (metaLabel, metaColor) = dueMeta(todo)
    var expanded by remember { mutableStateOf(false) }
    // A finished task has nothing left to schedule, so tapping it does not
    // open the editor; long-press still offers archive and delete.
    val done = todo.status != "pending"
    val hasSteps = todo.subtasks.isNotEmpty()
    val stepsDone = todo.subtasks.count { it.completed }
    val shape = RoundedCornerShape(13.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                onClick = { if (!done) onEdit() },
                onLongClick = onLongPress
            )
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 7.dp)
                .width(3.dp)
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (done) MaterialTheme.colorScheme.outlineVariant else accent)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CheckToggle(
                    checked = todo.status == "completed",
                    onClick = onToggle,
                    contentDescription = if (done) "Mark as not done" else "Mark as done"
                )
                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(metaLabel)
                            if (hasSteps) append(" · $stepsDone of ${todo.subtasks.size} steps")
                            if (todo.tags.isNotEmpty()) {
                                append(" · ")
                                append(todo.tags.joinToString(" ") { "#$it" })
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = metaColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasSteps) {
                    // Steps are worked from the list, not from inside the editor.
                    CompactIconButton(
                        icon = if (expanded) Icons.Rounded.ExpandLess
                        else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Hide steps"
                        else "Show ${todo.subtasks.size} steps",
                        onClick = { expanded = !expanded }
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            AnimatedVisibility(visible = expanded && hasSteps) {
                Column(modifier = Modifier.padding(start = 38.dp, end = 12.dp, bottom = 8.dp)) {
                    todo.subtasks.forEach { subtask ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable { onToggleSubtask(subtask) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (subtask.completed) Icons.Rounded.CheckCircle
                                else Icons.Outlined.Circle,
                                contentDescription = null,
                                tint = if (subtask.completed) accentOf(Accents.Habit)
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(9.dp))
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
}

/** A habit shown under a picked day, tickable when that day has arrived. */
@Composable
private fun DayHabitRow(
    uid: String,
    habit: Habit,
    date: LocalDate,
    today: LocalDate,
    accent: Color
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val key = date.toString()
    val done = key in habit.completionDates
    // A habit cannot be completed before the day arrives, and only today can
    // be un-ticked, so future days are read-only here.
    val future = date.isAfter(today)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .heightIn(min = 46.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 7.dp)
                .width(3.dp)
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        CheckToggle(
            checked = done,
            enabled = !future && !(done && date != today),
            contentDescription = if (done) "Done" else "Mark done",
            onClick = {
                scope.launch {
                    try {
                        if (date == today) {
                            HabitRepo.toggleToday(uid, habit)
                        } else {
                            HabitRepo.markDoneOn(uid, habit.id, key)
                        }
                    } catch (e: Exception) {
                        notify(scope, snackbar, e.message ?: "Couldn't update that")
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
                text = if (habit.reminderTime.isNotBlank()) {
                    "Habit at ${habit.reminderTime}"
                } else {
                    "Habit"
                },
                style = MaterialTheme.typography.bodySmall,
                color = accent
            )
        }
        Spacer(Modifier.width(10.dp))
    }
}

// ── Editors ──────────────────────────────────────────────────────────────────

/**
 * The task editor. Public because Today opens it too: the add button belongs
 * on whichever screen you are standing on, not only on Tasks.
 */
@Composable
fun TaskEditorSheet(
    uid: String,
    existing: Todo?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val zone = ZoneId.systemDefault()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var date by remember {
        mutableStateOf(timestampLocalDate(existing?.scheduledDate) ?: defaultDate)
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
            val scheduled = Timestamp(Date.from(date.atTime(time).atZone(zone).toInstant()))
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
                onValueChange = { date = it ?: defaultDate },
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
                    icon = Icons.Rounded.Close,
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
                icon = Icons.Rounded.Add,
                contentDescription = "Add step",
                onClick = { commitStep() }
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun RoutineEditorSheet(uid: String, existing: Routine?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    val items = remember {
        (existing?.items ?: listOf(RoutineItem("", "medium", emptyList(), emptyList(), "")))
            .toMutableStateList()
    }

    fun snapshot(): List<Any?> = listOf(title, items.toList())
    val original = remember { snapshot() }

    EditorSheet(
        title = if (existing == null) "New routine" else "Edit routine",
        dirty = snapshot() != original,
        onDismiss = onDismiss,
        onConfirm = {
            val cleanTitle = title.trim()
            val cleanItems = items.map { it.copy(title = it.title.trim()) }
                .filter { it.title.isNotEmpty() }
            if (cleanTitle.isEmpty() || cleanItems.isEmpty()) {
                notify(scope, snackbar, "Add a name and at least one step.")
                return@EditorSheet
            }
            scope.launch {
                try {
                    RoutineRepo.save(uid, existing?.id, cleanTitle, cleanItems)
                    onDismiss()
                } catch (e: Exception) {
                    notify(scope, snackbar, e.message ?: "Couldn't save that routine")
                }
            }
        }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(60) },
            label = { Text("Routine name") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Steps", style = MaterialTheme.typography.labelLarge)
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.title,
                    onValueChange = { items[index] = item.copy(title = it.take(60)) },
                    label = { Text("Step ${index + 1}") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { if (items.size > 1) items.removeAt(index) },
                    enabled = items.size > 1
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove step")
                }
            }
        }
        SecondaryButton(
            text = "Add step",
            icon = Icons.Rounded.Add,
            onClick = { items.add(RoutineItem("", "medium", emptyList(), emptyList(), "")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
    }
}
