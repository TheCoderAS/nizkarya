@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nizkarya.app.data.Todo
import com.nizkarya.app.ui.theme.StreakFlame
import com.nizkarya.app.ui.theme.SuccessDark
import com.nizkarya.app.ui.theme.SuccessLight
import com.nizkarya.app.ui.theme.WarningDark
import com.nizkarya.app.ui.theme.WarningLight
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** App-wide snackbar host — replaces Toasts, which look dated on modern Android. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

fun notify(scope: CoroutineScope, host: SnackbarHostState, message: String) {
    scope.launch {
        host.currentSnackbarData?.dismiss()
        host.showSnackbar(message)
    }
}

// ── Semantic colours that respect the active theme ───────────────────────────

@Composable
fun successColor(): Color =
    if (MaterialTheme.colorScheme.background.luminanceIsDark()) SuccessDark else SuccessLight

@Composable
fun warningColor(): Color =
    if (MaterialTheme.colorScheme.background.luminanceIsDark()) WarningDark else WarningLight

@Composable
fun flameColor(): Color = StreakFlame

private fun Color.luminanceIsDark(): Boolean =
    (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5

@Composable
fun priorityColor(priority: String): Color = when (priority) {
    "high" -> MaterialTheme.colorScheme.error
    "medium" -> warningColor()
    else -> successColor()
}

fun priorityLabel(priority: String): String = when (priority) {
    "high" -> "High"
    "low" -> "Low"
    else -> "Medium"
}

// ── Date/time formatting ─────────────────────────────────────────────────────

private val dueFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
private val groupFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM")

fun formatDue(timestamp: Timestamp?): String {
    if (timestamp == null) return "No time set"
    return timestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).format(dueFormatter)
}

fun timestampLocalDate(timestamp: Timestamp?): LocalDate? =
    timestamp?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()

fun formatClock(timestamp: Timestamp?): String? =
    timestamp?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofPattern("HH:mm"))

/** Human status for a task: "Overdue", "In 2h 10m", "Done"… */
@Composable
fun dueMeta(todo: Todo): Pair<String, Color> {
    if (todo.status == "completed") return "Done" to successColor()
    if (todo.status == "skipped") return "Skipped" to MaterialTheme.colorScheme.onSurfaceVariant
    val ts = todo.scheduledDate
        ?: return "No time set" to MaterialTheme.colorScheme.onSurfaceVariant
    val diffMs = ts.toDate().time - System.currentTimeMillis()
    if (diffMs <= 0) return "Overdue" to MaterialTheme.colorScheme.error
    val totalMinutes = diffMs / 60000
    if (totalMinutes < 24 * 60) {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val label = if (hours > 0) "In ${hours}h ${minutes}m" else "In ${minutes}m"
        val tone = when {
            hours < 1 -> MaterialTheme.colorScheme.error
            hours <= 3 -> warningColor()
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        return label to tone
    }
    return formatDue(ts) to MaterialTheme.colorScheme.onSurfaceVariant
}

fun groupLabel(date: LocalDate?, today: LocalDate): String = when (date) {
    null -> "No date"
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(groupFormatter)
}

// ── Reusable pieces ──────────────────────────────────────────────────────────

@Composable
fun PriorityDot(priority: String, size: Int = 8) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color = priorityColor(priority), shape = CircleShape)
    )
}

/** Single-choice segmented control — the modern Android filter pattern. */
@Composable
fun SegmentedChoice(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            )
        }
    }
}

/** Friendly empty state with an icon — never a bare sentence. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Section heading used between list groups. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
    )
}

/** Small stat used on Today / Insights / Profile. */
@Composable
fun StatPill(value: String, label: String, tint: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RowSpacer(width: Int = 8) = Spacer(Modifier.width(width.dp))
