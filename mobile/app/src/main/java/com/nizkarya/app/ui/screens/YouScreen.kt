@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nizkarya.app.BuildConfig
import com.nizkarya.app.data.AuthRepo
import com.nizkarya.app.data.AuthState
import com.nizkarya.app.data.Habit
import com.nizkarya.app.data.Todo
import com.nizkarya.app.logic.DayStreak
import com.nizkarya.app.logic.HabitLogic
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.ScreenHeader
import com.nizkarya.app.ui.components.SegmentedChoice
import com.nizkarya.app.ui.components.StatCard
import com.nizkarya.app.ui.components.TimelineDivider
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.components.timestampLocalDate
import com.nizkarya.app.ui.theme.Accents
import com.nizkarya.app.ui.theme.AppSettings
import com.nizkarya.app.ui.theme.accentOf
import com.nizkarya.app.ui.theme.heroGradient
import com.nizkarya.app.ui.theme.supportsDynamicColor
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val weekdayShort = DateTimeFormatter.ofPattern("EEE")

/** Everything the page shows about you, worked out in one pass. */
private data class Progress(
    val week: List<Pair<LocalDate, Int>>,
    val weekTotal: Int,
    val onTimePercent: Int,
    val dayStreak: Int,
    val pending: Int,
    val completed: Int,
    val habitCount: Int,
    val bestHabitStreak: Int,
    val habitConsistency: Int
) {
    companion object {
        fun of(todos: List<Todo>, habits: List<Habit>, today: LocalDate, zone: ZoneId): Progress {
            val active = todos.filter { it.archivedAt == null }
            val week = (6 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                date to active.count { todo ->
                    todo.status == "completed" &&
                        todo.completedDate?.toDate()?.toInstant()?.atZone(zone)
                            ?.toLocalDate() == date
                }
            }
            val completed = active.filter { it.status == "completed" }
            val onTime = completed.count { todo ->
                val sched = timestampLocalDate(todo.scheduledDate)
                val done = timestampLocalDate(todo.completedDate)
                sched != null && done != null && !done.isAfter(sched)
            }

            val activeHabits = habits.filter { it.archivedAt == null }
            var scheduled = 0
            var doneCount = 0
            activeHabits.forEach { habit ->
                val created = habit.createdAt?.toDate()?.toInstant()
                    ?.atZone(HabitLogic.zoneOf(habit.timezone))?.toLocalDate()
                for (back in 0..29) {
                    val date = today.minusDays(back.toLong())
                    if (created != null && date < created) continue
                    if (!HabitLogic.isScheduledOn(habit, date)) continue
                    scheduled++
                    if (date.toString() in habit.completionDates) doneCount++
                }
            }

            return Progress(
                week = week,
                weekTotal = week.sumOf { it.second },
                onTimePercent = if (completed.isNotEmpty()) {
                    (onTime * 100) / completed.size
                } else {
                    0
                },
                dayStreak = DayStreak.current(active, today),
                pending = active.count { it.status == "pending" },
                completed = completed.size,
                habitCount = activeHabits.size,
                bestHabitStreak = activeHabits.maxOfOrNull { HabitLogic.currentStreak(it) } ?: 0,
                habitConsistency = if (scheduled > 0) (doneCount * 100) / scheduled else 0
            )
        }
    }
}

/**
 * You: how it is going, and the switches.
 *
 * Insights used to be a separate screen reached from a card on the dashboard,
 * which meant almost nobody saw it. It lives here now, above the settings,
 * because this is where you already come to look at your own numbers.
 */
@Composable
fun YouScreen(
    user: AuthState.SignedIn,
    todos: List<Todo>,
    habits: List<Habit>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    val amber = accentOf(Accents.Streak)
    val mint = accentOf(Accents.Habit)
    val late = accentOf(Accents.Late)

    val p = remember(todos, habits, today) { Progress.of(todos, habits, today, zone) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notify(
            scope,
            snackbar,
            if (granted) "Reminders are on." else "Reminders stay off until you allow them."
        )
    }

    val initials = user.displayName.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2).joinToString("")
        .ifBlank { user.email.take(1).uppercase() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)
    ) {
        item(key = "identity") {
            ScreenHeader(title = "You")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(heroGradient()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.displayName.ifBlank { "Your account" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatCard(
                    value = "${p.dayStreak}",
                    label = "Day streak",
                    tint = amber,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = "${p.completed}",
                    label = "Tasks done",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatCard(
                    value = "${p.pending}",
                    label = "Still open",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = "${p.habitConsistency}%",
                    label = "Habit consistency",
                    tint = mint,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "chart") {
            TimelineDivider("Last 7 days")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${p.weekTotal}",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (p.weekTotal == 1) "task finished" else "tasks finished",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${p.onTimePercent}% on time",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (p.onTimePercent >= 60) mint else late,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                WeekBars(week = p.week, tint = MaterialTheme.colorScheme.primary, peak = mint)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    p.week.forEach { (date, _) ->
                        Text(
                            text = date.format(weekdayShort),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                StatCard(
                    value = "${p.habitCount}",
                    label = "Active habits",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = "${p.bestHabitStreak}",
                    label = "Longest habit run",
                    tint = amber,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "appearance") {
            TimelineDivider("Appearance")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (supportsDynamicColor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingIcon(Icons.Rounded.Palette)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Match my wallpaper",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Use the colours from your home screen instead",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = AppSettings.dynamicColor,
                            onCheckedChange = { AppSettings.setDynamicColor(context, it) }
                        )
                    }
                }
                Column {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(7.dp))
                    SegmentedChoice(
                        options = listOf(
                            "system" to "Auto",
                            "light" to "Light",
                            "dark" to "Dark"
                        ),
                        selected = AppSettings.themeMode,
                        onSelect = { AppSettings.setThemeMode(context, it) }
                    )
                }
            }
        }

        item(key = "reminders") {
            TimelineDivider("Reminders")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(
                                android.Manifest.permission.POST_NOTIFICATIONS
                            )
                        } else {
                            notify(scope, snackbar, "Reminders are already on.")
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingIcon(Icons.Rounded.Notifications)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Task and habit reminders", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Anything with a time notifies you on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Allow",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item(key = "signout") {
            Spacer(Modifier.height(18.dp))
            // Quiet and error-tinted: leaving is neither the page's action nor
            // a thing to make attractive, but it should read as consequential.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { AuthRepo.signOut() }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    tint = late,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Sign out",
                    style = MaterialTheme.typography.titleMedium,
                    color = late
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "NizKarya ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp)
        )
    }
}

/** Seven bars, with the best day picked out. */
@Composable
private fun WeekBars(week: List<Pair<LocalDate, Int>>, tint: Color, peak: Color) {
    val maxCount = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier = Modifier.fillMaxWidth().height(84.dp)) {
        val barCount = week.size
        if (barCount == 0) return@Canvas
        val gap = size.width * 0.035f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val radius = CornerRadius(barWidth / 3.2f)
        week.forEachIndexed { index, (_, count) ->
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = track,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = radius
            )
            if (count > 0) {
                val barHeight = size.height * (count.toFloat() / maxCount)
                drawRoundRect(
                    color = if (count == maxCount) peak else tint,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius
                )
            }
        }
    }
}
