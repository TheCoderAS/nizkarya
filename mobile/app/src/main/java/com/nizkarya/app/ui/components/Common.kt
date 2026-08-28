package com.nizkarya.app.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nizkarya.app.data.Todo
import com.nizkarya.app.ui.theme.Danger
import com.nizkarya.app.ui.theme.Success
import com.nizkarya.app.ui.theme.Warning
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun priorityColor(priority: String): Color = when (priority) {
    "high" -> Danger
    "medium" -> Warning
    else -> Success
}

private val dueFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
private val groupFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy · EEE")

fun formatDue(timestamp: Timestamp?): String {
    if (timestamp == null) return "No due time"
    val zoned = timestamp.toDate().toInstant().atZone(ZoneId.systemDefault())
    return zoned.format(dueFormatter)
}

fun timestampLocalDate(timestamp: Timestamp?): LocalDate? =
    timestamp?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate()

/** "Overdue" / "Due in Xh Ym" / formatted date, with a status color. */
fun dueMeta(todo: Todo): Pair<String, Color> {
    if (todo.status == "completed") return "Completed" to Success
    if (todo.status == "skipped") return "Skipped" to Color.Gray
    val ts = todo.scheduledDate ?: return "No due time" to Color.Gray
    val diffMs = ts.toDate().time - System.currentTimeMillis()
    if (diffMs <= 0) return "Overdue" to Danger
    val totalMinutes = diffMs / 60000
    return if (totalMinutes < 24 * 60) {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        "Due in ${hours}h ${minutes}m" to if (hours < 1) Danger else if (hours <= 3) Warning else Success
    } else {
        formatDue(ts) to Color.Gray
    }
}

fun groupLabel(date: LocalDate?, today: LocalDate): String = when (date) {
    null -> "Unscheduled"
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(groupFormatter)
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PriorityDot(priority: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(priorityColor(priority))
    )
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth()
    )
}

/** Row of small pill buttons where exactly one option is selected. */
@Composable
fun ChoiceRow(
    options: List<Pair<String, String>>, // value to label
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            if (value == selected) {
                Button(onClick = { onSelect(value) }) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }) { Text(label) }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp)
    )
}
