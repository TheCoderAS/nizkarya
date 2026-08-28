@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nizkarya.app.data.Todo
import com.nizkarya.app.ui.theme.SuccessDark
import com.nizkarya.app.ui.theme.SuccessLight
import com.nizkarya.app.ui.theme.WarningDark
import com.nizkarya.app.ui.theme.WarningLight
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** App-wide snackbar host. Replaces Toasts, which look dated on modern Android. */
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

/** Streaks are shown as plain numbers, tinted so they still read as a reward. */
@Composable
fun streakColor(): Color = MaterialTheme.colorScheme.tertiary

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
fun PriorityDot(priority: String, size: Int = 7) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color = priorityColor(priority), shape = CircleShape)
    )
}

/**
 * Dense list row. Material's own [androidx.compose.material3.ListItem] enforces
 * a 56–72dp minimum height, which leaves the app looking half empty; this keeps
 * the same anatomy at roughly two-thirds the height.
 */
@Composable
fun CompactRow(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(
                start = if (leading == null) 12.dp else 4.dp,
                end = if (trailing == null) 12.dp else 4.dp,
                top = 6.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(2.dp))
        }
        Column(modifier = Modifier.weight(1f), content = content)
        if (trailing != null) {
            Spacer(Modifier.width(2.dp))
            trailing()
        }
    }
}

/**
 * Circular check toggle sized for dense rows. [IconButton] reserves 48dp, which
 * is more than a list row should give away; 38dp still clears the accessible
 * touch-target floor once the row's own padding is counted.
 */
@Composable
fun CheckToggle(
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.outlineVariant
                checked -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(21.dp)
        )
    }
}

/** Overflow button matched to [CompactRow]'s height budget. */
@Composable
fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (tint == Color.Unspecified) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                tint
            },
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Single-choice segmented control: the modern Android filter pattern. */
@Composable
fun SegmentedChoice(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth().height(36.dp)) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
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

/** Friendly empty state with an icon, never a bare sentence. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Section heading used between list groups. Sentence case on purpose:
 * SHOUTED HEADINGS read as noise and wreck dates like "Wednesday, 3 Sep".
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 1.dp)
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
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RowSpacer(width: Int = 6) = Spacer(Modifier.width(width.dp))
