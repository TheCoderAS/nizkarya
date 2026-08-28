@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nizkarya.app.data.AuthState
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.CalendarLoad
import com.nizkarya.app.logic.DayStreak
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.logic.QuickAddParser
import com.nizkarya.app.ui.components.CheckToggle
import com.nizkarya.app.ui.components.CompactRow
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.PriorityDot
import com.nizkarya.app.ui.components.SectionLabel
import com.nizkarya.app.ui.components.VoiceInputButton
import com.nizkarya.app.ui.components.formatClock
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.components.streakColor
import com.nizkarya.app.ui.components.timestampLocalDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlinx.coroutines.launch

/** Today shows a slice; the rest live in Plan behind a "see all". */
private const val TODAY_TASK_LIMIT = 6

private val weekInitials = listOf("S", "M", "T", "W", "T", "F", "S")

/** Everything the dashboard derives from the raw lists, computed in one pass. */
private data class TodaySummary(
    val pendingToday: List<Todo>,
    val todayHabits: List<Habit>,
    val habitsDone: Int,
    val overdueCount: Int,
    val total: Int,
    val done: Int,
    val percent: Int,
    val perfect: Boolean,
    val dayStreak: Int
) {
    companion object {
        fun of(todos: List<Todo>, habits: List<Habit>, today: LocalDate): TodaySummary {
            val active = todos.filter { it.archivedAt == null }
            val todayTodos = active
                .filter { timestampLocalDate(it.scheduledDate) == today }
                .sortedBy { it.scheduledDate?.seconds ?: 0L }
            val todayHabits =
                habits.filter { it.archivedAt == null && HabitLogic.isScheduledToday(it) }
            val habitsDone = todayHabits.count { HabitLogic.isDoneToday(it) }
            val doneTodoCount = todayTodos.count { it.status == "completed" }
            val total = todayTodos.size + todayHabits.size
            val done = doneTodoCount + habitsDone
            return TodaySummary(
                pendingToday = todayTodos.filter { it.status == "pending" },
                todayHabits = todayHabits,
                habitsDone = habitsDone,
                overdueCount = active.count {
                    it.status == "pending" && it.scheduledDate != null &&
                        (timestampLocalDate(it.scheduledDate) ?: today) < today
                },
                total = total,
                done = done,
                percent = if (total > 0) (done * 100) / total else 0,
                perfect = total > 0 && done == total,
                dayStreak = DayStreak.current(active, today)
            )
        }
    }
}

@Composable
fun TodayScreen(
    user: AuthState.SignedIn,
    todos: List<Todo>,
    habits: List<Habit>,
    onOpenReview: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenDay: (LocalDate) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val today = LocalDate.now()

    // The habit predicates parse a time zone per call and DayStreak walks back
    // through days, so the whole derivation is memoized rather than rerun on
    // every frame of a navigation animation.
    val d = remember(todos, habits, today) { TodaySummary.of(todos, habits, today) }
    val pendingToday = d.pendingToday
    val todayHabits = d.todayHabits
    val habitsDone = d.habitsDone
    val overdueCount = d.overdueCount
    val total = d.total
    val done = d.done
    val percent = d.percent
    val perfect = d.perfect
    val dayStreak = d.dayStreak

    val greeting = when (LocalTime.now().hour) {
        in 0..4 -> "Still up"
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Winding down"
    }
    val firstName = user.displayName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
        ?: user.email.substringBefore("@")

    fun addByVoice(spoken: String) {
        val parsed = QuickAddParser.parse(spoken)
        if (parsed.title.isBlank()) {
            notify(scope, snackbar, "Didn't catch a task in that.")
            return
        }
        val zone = ZoneId.systemDefault()
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
                    user.uid, parsed.title, scheduled, parsed.priority,
                    parsed.tags, emptyList(), "", null, emptyList()
                )
                notify(scope, snackbar, "Added “${parsed.title}”")
            } catch (e: Exception) {
                notify(scope, snackbar, e.message ?: "Couldn't add that")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            // Greeting, date and streak collapse into two lines. The old header
            // spent five before a single task appeared.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting, $firstName",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row {
                        Text(
                            text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (dayStreak > 0) {
                            Text(
                                text = "  ·  $dayStreak day streak",
                                style = MaterialTheme.typography.bodySmall,
                                color = streakColor()
                            )
                        }
                    }
                }
                VoiceInputButton(onResult = { addByVoice(it) })
            }
        }

        item {
            // A week at a glance rather than a month grid: the dashboard is
            // about what is close, and a full calendar here would undo the
            // density work. Tapping a day opens the month view on it.
            WeekAhead(
                todos = todos,
                habits = habits,
                today = today,
                onSelect = onOpenDay
            )
        }

        if (perfect) item { PerfectDayCard() }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProgressRing(percent)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$done of $total done",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (total == 0) "Nothing planned yet"
                            else "${pendingToday.size} tasks · " +
                                "${todayHabits.size - habitsDone} habits left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (overdueCount > 0) {
            item {
                Card(
                    onClick = onOpenReview,
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    CompactRow(
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    ) {
                        Text(
                            "$overdueCount overdue",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Tap to replan them into today",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (pendingToday.isNotEmpty()) {
            item { SectionLabel("Today's tasks") }
            items(pendingToday.take(TODAY_TASK_LIMIT), key = { it.id }) { todo ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    CompactRow(
                        leading = {
                            CheckToggle(
                                checked = false,
                                contentDescription = "Mark done",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        runCatching { TodoRepo.toggleStatus(user.uid, todo) }
                                    }
                                }
                            )
                        }
                    ) {
                        Text(
                            text = todo.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PriorityDot(todo.priority)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = formatClock(todo.scheduledDate) ?: "No time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            // The list used to stop at six with no hint that more existed.
            if (pendingToday.size > TODAY_TASK_LIMIT) {
                item {
                    TextButton(onClick = onOpenTasks) {
                        Text("See all ${pendingToday.size} tasks")
                    }
                }
            }
        }

        if (todayHabits.isNotEmpty()) {
            item { SectionLabel("Habits") }
            items(todayHabits, key = { it.id }) { habit ->
                val isDone = HabitLogic.isDoneToday(habit)
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    val streak = HabitLogic.currentStreak(habit)
                    val streakBadge: (@Composable () -> Unit)? = if (streak > 0) {
                        {
                            Text(
                                text = "${streak}d",
                                style = MaterialTheme.typography.labelLarge,
                                color = streakColor(),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    } else {
                        null
                    }
                    CompactRow(
                        leading = {
                            CheckToggle(
                                checked = isDone,
                                contentDescription = if (isDone) "Undo" else "Mark done",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        runCatching { HabitRepo.toggleToday(user.uid, habit) }
                                    }
                                }
                            )
                        },
                        trailing = streakBadge
                    ) {
                        Text(
                            text = habit.title,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        item {
            Card(
                onClick = onOpenInsights,
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                CompactRow(
                    leading = {
                        Icon(
                            Icons.Outlined.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp).size(20.dp)
                        )
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                ) {
                    Text(
                        "Your insights",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Trends, streaks and how the week is going",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/** Seven days from today, with a dot for anything scheduled. */
@Composable
private fun WeekAhead(
    todos: List<Todo>,
    habits: List<Habit>,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val load = remember(todos, habits, today) {
        CalendarLoad.forRange(todos, habits, today, 7)
    }
    val scheme = MaterialTheme.colorScheme

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            (0 until 7).forEach { offset ->
                val date = today.plusDays(offset.toLong())
                val day = load[date]
                val isToday = offset == 0
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onSelect(date) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = weekInitials[date.dayOfWeek.value % 7],
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .then(
                                if (isToday) Modifier.background(scheme.primary, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) scheme.onPrimary else scheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier.size(4.dp).then(
                            when {
                                day == null || day.total == 0 -> Modifier
                                day.allDone -> Modifier.background(scheme.primary, CircleShape)
                                else -> Modifier.background(scheme.onSurfaceVariant, CircleShape)
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PerfectDayCard() {
    val transition = rememberInfiniteTransition(label = "perfect")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(26.dp).scale(pulse)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Perfect day",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Everything planned for today is done.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(percent: Int) {
    val animated by animateFloatAsState(
        targetValue = percent.coerceIn(0, 100) / 100f,
        animationSpec = tween(700),
        label = "ring"
    )
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
    val accent = MaterialTheme.colorScheme.onPrimaryContainer
    Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 18f, cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(accent, -90f, 360f * animated, false, style = stroke)
        }
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
