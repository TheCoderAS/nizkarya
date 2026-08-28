package com.nizkarya.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.nizkarya.app.ui.components.PriorityDot
import com.nizkarya.app.ui.components.VoiceInputButton
import com.nizkarya.app.ui.components.timestampLocalDate
import com.nizkarya.app.ui.components.toast
import com.nizkarya.app.ui.theme.BrandCoral
import com.nizkarya.app.ui.theme.BrandViolet
import com.nizkarya.app.ui.theme.Warning
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
    onOpenPlan: () -> Unit,
    onOpenInsights: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    val activeTodos = todos.filter { it.archivedAt == null }
    val todayTodos = activeTodos
        .filter { timestampLocalDate(it.scheduledDate) == today }
        .sortedBy { it.scheduledDate?.seconds ?: 0L }
    val pendingToday = todayTodos.filter { it.status == "pending" }
    val completedTodayCount = todayTodos.count { it.status == "completed" }
    val overdueCount = activeTodos.count {
        it.status == "pending" && it.scheduledDate != null &&
            (timestampLocalDate(it.scheduledDate) ?: today) < today
    }
    val todayHabits = habits.filter { it.archivedAt == null && HabitLogic.isScheduledToday(it) }
    val habitsDone = todayHabits.count { HabitLogic.isDoneToday(it) }

    val totalToday = todayTodos.size + todayHabits.size
    val doneToday = completedTodayCount + habitsDone
    val percent = if (totalToday > 0) (doneToday * 100) / totalToday else 0
    val perfectDay = totalToday > 0 && doneToday == totalToday
    val dayStreak = DayStreak.current(activeTodos, today)

    val greeting = when (LocalTime.now().hour) {
        in 0..4 -> "Still up?"
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
            toast(context, "Couldn't catch a task in that.")
            return
        }
        val zone = ZoneId.systemDefault()
        val scheduled: Timestamp? = when {
            parsed.date != null -> {
                val time = parsed.time ?: LocalTime.of(9, 0)
                Timestamp(Date.from(parsed.date.atTime(time).atZone(zone).toInstant()))
            }
            parsed.time != null ->
                Timestamp(Date.from(today.atTime(parsed.time).atZone(zone).toInstant()))
            else -> null
        }
        scope.launch {
            try {
                TodoRepo.add(
                    user.uid, parsed.title, scheduled, parsed.priority,
                    parsed.tags, emptyList(), "", null, emptyList()
                )
                toast(context, "Added: ${parsed.title}")
            } catch (e: Exception) {
                toast(context, e.message ?: "Unable to add task")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = firstName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (dayStreak > 0) {
                    Text(
                        text = "🔥 $dayStreak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                VoiceInputButton(onResult = { addByVoice(it) })
            }
        }

        item {
            Text(
                text = "“${Motivation.forDate(today)}”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (perfectDay) {
            item { PerfectDayBanner() }
        }

        item {
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProgressRing(percent = percent)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Today's momentum",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$doneToday / $totalToday",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${pendingToday.size} tasks · " +
                                "${todayHabits.size - habitsDone} habits left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onStartFocus) {
                            Icon(Icons.Filled.Bolt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Start a focus sprint")
                        }
                    }
                }
            }
        }

        if (overdueCount > 0) {
            item {
                Card(modifier = Modifier.clickable { onOpenPlan() }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "⏰", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "$overdueCount overdue item" +
                                (if (overdueCount == 1) "" else "s") +
                                " — tap to replan",
                            color = Warning
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Today's tasks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (pendingToday.isEmpty()) {
            item {
                Text(
                    text = if (todayTodos.isNotEmpty()) "All of today's tasks are done. 🎉"
                    else "Nothing scheduled for today — say it with the mic above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(pendingToday.take(6), key = { it.id }) { todo ->
                Card(modifier = Modifier.clickable { onOpenPlan() }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PriorityDot(todo.priority)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = todo.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val time = todo.scheduledDate?.toDate()?.toInstant()
                            ?.atZone(ZoneId.systemDefault())?.toLocalTime()
                        if (time != null) {
                            Text(
                                text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (todayHabits.isNotEmpty()) {
            item {
                Text(
                    text = "Habits today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(todayHabits, key = { it.id }) { habit ->
                val done = HabitLogic.isDoneToday(habit)
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        HabitRepo.toggleToday(user.uid, habit)
                                    } catch (e: Exception) {
                                        toast(context, e.message ?: "Unable to update habit")
                                    }
                                }
                            }
                        ) {
                            if (done) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Done",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            } else {
                                Icon(Icons.Outlined.Circle, contentDescription = "Mark done")
                            }
                        }
                        Text(
                            text = habit.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                        val streak = HabitLogic.currentStreak(habit)
                        if (streak > 0) {
                            Text(
                                text = "🔥 $streak",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.clickable { onOpenInsights() }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your insights",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Trends, streaks & how your week is going",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun PerfectDayBanner() {
    val transition = rememberInfiniteTransition(label = "perfectDay")
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎉",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.scale(pulse)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Perfect day!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Everything scheduled today is done. Own it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(percent: Int) {
    val sweep = 360f * percent.coerceIn(0, 100) / 100f
    Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        val track = MaterialTheme.colorScheme.surfaceVariant
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 26f, cap = StrokeCap.Round)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(BrandViolet, BrandCoral),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = stroke
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "done",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}
