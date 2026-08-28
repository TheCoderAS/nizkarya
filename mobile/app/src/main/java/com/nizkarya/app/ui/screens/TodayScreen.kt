@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nizkarya.app.data.AuthState
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.HabitRepo
import com.nizkarya.app.data.Todo
import com.nizkarya.app.data.TodoRepo
import com.nizkarya.app.logic.DayStreak
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.logic.Motivation
import com.nizkarya.app.logic.QuickAddParser
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.PriorityDot
import com.nizkarya.app.ui.components.SectionLabel
import com.nizkarya.app.ui.components.VoiceInputButton
import com.nizkarya.app.ui.components.flameColor
import com.nizkarya.app.ui.components.formatClock
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.components.timestampLocalDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    user: AuthState.SignedIn,
    todos: List<Todo>,
    habits: List<Habit>,
    onStartFocus: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenInsights: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val today = LocalDate.now()

    val active = todos.filter { it.archivedAt == null }
    val todayTodos = active
        .filter { timestampLocalDate(it.scheduledDate) == today }
        .sortedBy { it.scheduledDate?.seconds ?: 0L }
    val pendingToday = todayTodos.filter { it.status == "pending" }
    val doneTodoCount = todayTodos.count { it.status == "completed" }
    val overdueCount = active.count {
        it.status == "pending" && it.scheduledDate != null &&
            (timestampLocalDate(it.scheduledDate) ?: today) < today
    }
    val todayHabits = habits.filter { it.archivedAt == null && HabitLogic.isScheduledToday(it) }
    val habitsDone = todayHabits.count { HabitLogic.isDoneToday(it) }

    val total = todayTodos.size + todayHabits.size
    val done = doneTodoCount + habitsDone
    val percent = if (total > 0) (done * 100) / total else 0
    val perfect = total > 0 && done == total
    val dayStreak = DayStreak.current(active, today)

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
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = firstName, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (dayStreak > 0) {
                    Text(
                        text = "🔥 $dayStreak",
                        style = MaterialTheme.typography.titleMedium,
                        color = flameColor()
                    )
                }
                VoiceInputButton(onResult = { addByVoice(it) })
            }
        }

        item {
            Text(
                text = Motivation.forDate(today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProgressRing(percent)
                    Spacer(Modifier.padding(horizontal = 9.dp))
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
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = onStartFocus) {
                            Icon(Icons.Filled.Bolt, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 3.dp))
                            Text("Focus")
                        }
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
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(
                                "$overdueCount overdue",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        },
                        supportingContent = {
                            Text(
                                "Tap to replan them into today",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    )
                }
            }
        }

        if (pendingToday.isNotEmpty()) {
            item { SectionLabel("Today's tasks") }
            items(pendingToday.take(6), key = { it.id }) { todo ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        runCatching { TodoRepo.toggleStatus(user.uid, todo) }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Circle,
                                    contentDescription = "Mark done",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        },
                        headlineContent = { Text(todo.title) },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PriorityDot(todo.priority)
                                Spacer(Modifier.padding(horizontal = 3.dp))
                                Text(
                                    text = formatClock(todo.scheduledDate) ?: "No time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
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
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        runCatching { HabitRepo.toggleToday(user.uid, habit) }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isDone) Icons.Filled.CheckCircle
                                    else Icons.Outlined.Circle,
                                    contentDescription = null,
                                    tint = if (isDone) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                            }
                        },
                        headlineContent = {
                            Text(
                                text = habit.title,
                                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingContent = {
                            val streak = HabitLogic.currentStreak(habit)
                            if (streak > 0) {
                                Text(
                                    "🔥$streak",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = flameColor()
                                )
                            }
                        }
                    )
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
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            Icons.Outlined.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    headlineContent = {
                        Text(
                            "Your insights",
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    supportingContent = {
                        Text(
                            "Trends, streaks and how the week is going",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                )
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎉",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.scale(pulse)
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
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
    Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 22f, cap = StrokeCap.Round)
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
