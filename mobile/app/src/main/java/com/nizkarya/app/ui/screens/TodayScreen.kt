@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nizkarya.app.LaunchIntents
import com.nizkarya.app.data.AuthState
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.DayStreak
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.logic.QuickAddParser
import com.nizkarya.app.logic.UndoWindow
import com.nizkarya.app.ui.components.AccentFab
import com.nizkarya.app.ui.components.ActionSheet
import com.nizkarya.app.ui.components.CheckToggle
import com.nizkarya.app.ui.components.ConfirmDialog
import com.nizkarya.app.ui.components.DayHeader
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.NowLine
import com.nizkarya.app.ui.components.PrimaryCta
import com.nizkarya.app.ui.components.SheetAction
import com.nizkarya.app.ui.components.ThreadShape
import com.nizkarya.app.ui.components.TimelineBlock
import com.nizkarya.app.ui.components.TimelineDivider
import com.nizkarya.app.ui.components.TimelineRow
import com.nizkarya.app.ui.components.VoiceInputButton
import com.nizkarya.app.ui.components.formatDue
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.components.timestampLocalDate
import com.nizkarya.app.ui.theme.Accents
import com.nizkarya.app.ui.theme.accentOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlinx.coroutines.launch

private val headerDate = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val clock = DateTimeFormatter.ofPattern("HH:mm")

/**
 * One thing sitting on the day. Tasks and habits share the timeline, because
 * standing at 09:00 there is no difference between them: both are something
 * to do next.
 */
private sealed interface DayEntry {
    val key: String
    val at: LocalTime?

    data class TaskAt(val todo: Todo, override val at: LocalTime?) : DayEntry {
        override val key get() = "t-" + todo.id
    }

    data class HabitAt(val habit: Habit, override val at: LocalTime?, val done: Boolean) :
        DayEntry {
        override val key get() = "h-" + habit.id
    }
}

/** Everything the day is derived from, computed in one pass. */
private data class Day(
    val timed: List<DayEntry>,
    val anytime: List<DayEntry>,
    val overdue: List<Todo>,
    val done: Int,
    val total: Int,
    val streak: Int
) {
    companion object {
        fun of(todos: List<Todo>, habits: List<Habit>, today: LocalDate, zone: ZoneId): Day {
            val active = todos.filter { it.archivedAt == null }
            val todayTodos = active.filter { timestampLocalDate(it.scheduledDate) == today }
            val todayHabits =
                habits.filter { it.archivedAt == null && HabitLogic.isScheduledToday(it) }

            val entries = buildList<DayEntry> {
                todayTodos.forEach { todo ->
                    val at = todo.scheduledDate?.toDate()?.toInstant()?.atZone(zone)?.toLocalTime()
                    add(DayEntry.TaskAt(todo, at))
                }
                todayHabits.forEach { habit ->
                    val at = habit.reminderTime.takeIf { it.isNotBlank() }
                        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                    add(DayEntry.HabitAt(habit, at, HabitLogic.isDoneToday(habit)))
                }
            }

            val doneCount = todayTodos.count { it.status == "completed" } +
                todayHabits.count { HabitLogic.isDoneToday(it) }

            return Day(
                timed = entries.filter { it.at != null }.sortedBy { it.at },
                anytime = entries.filter { it.at == null },
                overdue = active.filter {
                    it.status == "pending" && it.scheduledDate != null &&
                        (timestampLocalDate(it.scheduledDate) ?: today) < today
                }.sortedBy { it.scheduledDate?.seconds ?: 0L },
                done = doneCount,
                total = entries.size,
                streak = DayStreak.current(active, today)
            )
        }
    }
}

@Composable
fun TodayScreen(
    user: AuthState.SignedIn,
    todos: List<Todo>,
    habits: List<Habit>,
    onOpenHabits: () -> Unit
) {
    val uid = user.uid
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val now = LocalTime.now()

    // Habit predicates parse a time zone per call and DayStreak walks back
    // through days, so the whole derivation is memoized rather than rerun on
    // every frame of a navigation animation.
    val day = remember(todos, habits, today) { Day.of(todos, habits, today, zone) }

    var catchUpOpen by remember { mutableStateOf(false) }
    // A widget or the Quick Settings tile can ask for the editor before this
    // screen exists. The request waits in LaunchIntents until it does.
    LaunchedEffect(LaunchIntents.pendingNewTask) {
        if (LaunchIntents.pendingNewTask) {
            editing = null
            editorOpen = true
            LaunchIntents.consume()
        }
    }
    var editing by remember { mutableStateOf<Todo?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var taskActions by remember { mutableStateOf<Todo?>(null) }
    var habitActions by remember { mutableStateOf<Habit?>(null) }
    var deleteAsk by remember { mutableStateOf<Todo?>(null) }

    val taskAccent = accentOf(Accents.Task)
    val habitAccent = accentOf(Accents.Habit)
    val streakAccent = accentOf(Accents.Streak)
    val lateAccent = accentOf(Accents.Late)

    // Index of the first thing still ahead of you. That is where the now line
    // goes, and it is where the eye is meant to start.
    val nowIndex = remember(day.timed, now.hour, now.minute) {
        day.timed.indexOfFirst { (it.at ?: LocalTime.MAX) >= now }
            .let { if (it < 0) day.timed.size else it }
    }

    fun toggleTodo(todo: Todo) {
        // A finished task whose moment has gone by is a record, not a switch.
        if (UndoWindow.isTodoSettled(todo)) {
            notify(scope, snackbar, UndoWindow.MESSAGE)
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            try {
                TodoRepo.toggleStatus(uid, todo)
            } catch (e: Exception) {
                notify(scope, snackbar, e.message ?: "Couldn't update that task")
            }
        }
    }

    fun toggleHabit(habit: Habit) {
        if (UndoWindow.isHabitSettled(habit, today)) {
            notify(scope, snackbar, UndoWindow.MESSAGE)
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            try {
                HabitRepo.toggleToday(uid, habit)
            } catch (e: Exception) {
                notify(scope, snackbar, e.message ?: "Couldn't update that habit")
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

    fun addByVoice(spoken: String) {
        val parsed = QuickAddParser.parse(spoken)
        if (parsed.title.isBlank()) {
            notify(scope, snackbar, "Didn't catch a task in that.")
            return
        }
        val scheduled: Timestamp? = when {
            parsed.date != null -> Timestamp(
                Date.from(
                    parsed.date.atTime(parsed.time ?: LocalTime.of(9, 0))
                        .atZone(zone).toInstant()
                )
            )
            parsed.time != null -> Timestamp(
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
                notify(scope, snackbar, "Added “${parsed.title}”")
            } catch (e: Exception) {
                notify(scope, snackbar, e.message ?: "Couldn't add that")
            }
        }
    }

    fun shapeAt(index: Int, size: Int): ThreadShape = when {
        size == 1 -> ThreadShape.Only
        index == 0 -> ThreadShape.First
        index == size - 1 -> ThreadShape.Last
        else -> ThreadShape.Middle
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)
        ) {
            item(key = "header") {
                DayHeader(
                    dateLabel = today.format(headerDate),
                    done = day.done,
                    total = day.total,
                    streak = day.streak,
                    trailing = { VoiceInputButton(onResult = { addByVoice(it) }) }
                )
                Spacer(Modifier.height(14.dp))
            }

            if (day.overdue.isNotEmpty()) {
                item(key = "catchup") {
                    CatchUpCard(
                        count = day.overdue.size,
                        accent = lateAccent,
                        onClick = { catchUpOpen = true }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (day.total == 0) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.WbSunny,
                        title = "Your day is clear",
                        subtitle = "Add something with the button below, or say it out loud."
                    )
                }
            }

            day.timed.forEachIndexed { index, entry ->
                if (index == nowIndex) {
                    item(key = "now") { NowLine(now.format(clock), lateAccent) }
                }
                item(key = entry.key) {
                    DayEntryRow(
                        entry = entry,
                        shape = shapeAt(index, day.timed.size),
                        now = now,
                        today = today,
                        taskAccent = taskAccent,
                        habitAccent = habitAccent,
                        streakAccent = streakAccent,
                        lateAccent = lateAccent,
                        onToggleTodo = ::toggleTodo,
                        onToggleHabit = ::toggleHabit,
                        onEditTodo = { editing = it; editorOpen = true },
                        onTaskActions = { taskActions = it },
                        onHabitActions = { habitActions = it }
                    )
                }
            }
            if (day.timed.isNotEmpty() && nowIndex >= day.timed.size) {
                item(key = "now") { NowLine(now.format(clock), lateAccent) }
            }

            if (day.anytime.isNotEmpty()) {
                item(key = "anytime") { TimelineDivider("Anytime") }
                items(day.anytime, key = { it.key }) { entry ->
                    DayEntryRow(
                        entry = entry,
                        shape = ThreadShape.Only,
                        now = now,
                        today = today,
                        taskAccent = taskAccent,
                        habitAccent = habitAccent,
                        streakAccent = streakAccent,
                        lateAccent = lateAccent,
                        onToggleTodo = ::toggleTodo,
                        onToggleHabit = ::toggleHabit,
                        onEditTodo = { editing = it; editorOpen = true },
                        onTaskActions = { taskActions = it },
                        onHabitActions = { habitActions = it }
                    )
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

    if (catchUpOpen) {
        CatchUpSheet(
            uid = uid,
            overdue = day.overdue,
            habits = habits,
            today = today,
            onDismiss = { catchUpOpen = false }
        )
    }

    taskActions?.let { todo ->
        ActionSheet(
            title = todo.title,
            actions = buildList {
                if (todo.status == "pending") {
                    add(
                        SheetAction(Icons.Rounded.Edit, "Edit") {
                            editing = todo
                            editorOpen = true
                        }
                    )
                }
                add(SheetAction(Icons.Rounded.Archive, "Archive") { archiveWithUndo(todo) })
                add(
                    SheetAction(Icons.Rounded.Delete, "Delete", destructive = true) {
                        deleteAsk = todo
                    }
                )
            },
            onDismiss = { taskActions = null }
        )
    }

    habitActions?.let { habit ->
        ActionSheet(
            title = habit.title,
            actions = listOf(
                SheetAction(Icons.Rounded.SkipNext, "Skip today") {
                    scope.launch {
                        runCatching { HabitRepo.skipOn(uid, habit.id, today.toString()) }
                    }
                },
                SheetAction(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    "Open in Habits"
                ) { onOpenHabits() }
            ),
            onDismiss = { habitActions = null }
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

    if (editorOpen) {
        TaskEditorSheet(
            uid = uid,
            existing = editing,
            defaultDate = today,
            onDismiss = { editorOpen = false }
        )
    }
}

@Composable
private fun DayEntryRow(
    entry: DayEntry,
    shape: ThreadShape,
    now: LocalTime,
    today: LocalDate,
    taskAccent: Color,
    habitAccent: Color,
    streakAccent: Color,
    lateAccent: Color,
    onToggleTodo: (Todo) -> Unit,
    onToggleHabit: (Habit) -> Unit,
    onEditTodo: (Todo) -> Unit,
    onTaskActions: (Todo) -> Unit,
    onHabitActions: (Habit) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    when (entry) {
        is DayEntry.TaskAt -> {
            val todo = entry.todo
            val complete = todo.status == "completed"
            val slipped = !complete && entry.at != null && entry.at < now
            val accent = if (slipped) lateAccent else taskAccent
            TimelineRow(
                time = entry.at?.format(clock),
                nodeColor = accent,
                filled = complete,
                shape = shape
            ) {
                TimelineBlock(
                    accent = accent,
                    onClick = { if (!complete) onEditTodo(todo) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTaskActions(todo)
                    },
                    leading = {
                        CheckToggle(
                            checked = complete,
                            settled = UndoWindow.isTodoSettled(todo),
                            contentDescription = if (complete) "Mark as not done"
                            else "Mark as done",
                            onClick = { onToggleTodo(todo) }
                        )
                    }
                ) {
                    Text(
                        text = todo.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (complete) TextDecoration.LineThrough else null,
                        color = if (complete) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val steps = todo.subtasks.size
                    val meta = buildString {
                        if (todo.priority == "high") append("High priority")
                        if (steps > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("${todo.subtasks.count { it.completed }} of $steps steps")
                        }
                        if (todo.tags.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(todo.tags.joinToString(" ") { "#$it" })
                        }
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (todo.priority == "high") lateAccent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        is DayEntry.HabitAt -> {
            val habit = entry.habit
            val streak = remember(habit, today) { HabitLogic.currentStreak(habit) }
            TimelineRow(
                time = entry.at?.format(clock),
                nodeColor = habitAccent,
                filled = entry.done,
                shape = shape
            ) {
                TimelineBlock(
                    accent = habitAccent,
                    onClick = { onToggleHabit(habit) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHabitActions(habit)
                    },
                    leading = {
                        CheckToggle(
                            checked = entry.done,
                            settled = UndoWindow.isHabitSettled(habit, today),
                            contentDescription = if (entry.done) "Undo" else "Mark done",
                            onClick = { onToggleHabit(habit) }
                        )
                    },
                    trailing = if (streak > 0) {
                        {
                            Text(
                                text = "$streak",
                                style = MaterialTheme.typography.titleMedium,
                                color = streakAccent
                            )
                        }
                    } else {
                        null
                    }
                ) {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (entry.done) TextDecoration.LineThrough else null,
                        color = if (entry.done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (habit.habitType == "avoid") "Habit to avoid" else "Habit",
                        style = MaterialTheme.typography.bodySmall,
                        color = habitAccent
                    )
                }
            }
        }
    }
}

/**
 * Review used to be a whole tab you had to remember to visit. It is now one
 * card, and it only exists on the days you are actually behind.
 */
@Composable
private fun CatchUpCard(count: Int, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.13f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (count == 1) "One thing slipped" else "$count things slipped",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Pull them into today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(text = "Catch up", style = MaterialTheme.typography.labelLarge, color = accent)
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * What is left of the Review tab: replan the lot at once, or deal with the
 * stragglers one at a time. Missed habits from the last week come along,
 * since they are the other half of being behind.
 */
@Composable
private fun CatchUpSheet(
    uid: String,
    overdue: List<Todo>,
    habits: List<Habit>,
    today: LocalDate,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val lateAccent = accentOf(Accents.Late)

    val missed = remember(habits, today) {
        habits.filter { it.archivedAt == null }.mapNotNull { habit ->
            val created = habit.createdAt?.toDate()?.toInstant()
                ?.atZone(HabitLogic.zoneOf(habit.timezone))?.toLocalDate()
            val dates = (1..7).map { today.minusDays(it.toLong()) }.filter { date ->
                (created == null || !date.isBefore(created)) &&
                    HabitLogic.isScheduledOn(habit, date) &&
                    date.toString() !in habit.completionDates &&
                    date.toString() !in habit.skippedDates
            }
            if (dates.isEmpty()) null else habit to dates
        }.sortedByDescending { it.second.size }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
        ) {
            Text("Catch up", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "${overdue.size} overdue" +
                    if (missed.isEmpty()) "" else " · ${missed.size} habits slipped",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            if (overdue.isNotEmpty()) {
                PrimaryCta(
                    text = "Move all ${overdue.size} into today",
                    icon = Icons.Rounded.Bolt,
                    height = 48.dp,
                    onClick = {
                        scope.launch {
                            try {
                                TodoRepo.replanIntoToday(uid, overdue)
                                notify(scope, snackbar, "Moved ${overdue.size} into today")
                                onDismiss()
                            } catch (e: Exception) {
                                notify(scope, snackbar, e.message ?: "Couldn't replan those")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                modifier = Modifier.height(340.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(overdue, key = { "o-" + it.id }) { todo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = todo.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Was due ${formatDue(todo.scheduledDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = lateAccent
                            )
                        }
                        SheetRowAction("Today") {
                            scope.launch {
                                runCatching { TodoRepo.rescheduleToToday(uid, todo) }
                            }
                        }
                        SheetRowAction("Skip") {
                            scope.launch { runCatching { TodoRepo.skip(uid, todo.id) } }
                        }
                    }
                }

                if (missed.isNotEmpty()) {
                    item(key = "missed-label") { TimelineDivider("Missed habits") }
                }
                items(missed, key = { "m-" + it.first.id }) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pair.first.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${pair.second.size} missed in the last week",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRowAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}
