@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.nizkarya.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Routine
import com.nizkarya.app.data.RoutineItem
import com.nizkarya.app.data.RoutineRepo
import com.nizkarya.app.ui.components.AccentFab
import com.nizkarya.app.ui.components.ActionSheet
import com.nizkarya.app.ui.components.CompactIconButton
import com.nizkarya.app.ui.components.ConfirmDialog
import com.nizkarya.app.ui.components.EditorSheet
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.ScreenHeader
import com.nizkarya.app.ui.components.SecondaryButton
import com.nizkarya.app.ui.components.SheetAction
import com.nizkarya.app.ui.components.TimeField
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.theme.Accents
import com.nizkarya.app.ui.theme.accentOf
import java.time.LocalTime
import kotlinx.coroutines.launch

/**
 * Managing routines, on a screen of its own.
 *
 * Deliberately not a fifth tab. A tab is somewhere you go every day, and
 * editing a routine is something you do twice a year; running one is the daily
 * act, and that stays as a strip on Tasks where the day is. This is the place
 * you push into when you actually want to work on the routine itself, and it
 * has the room a chip in a strip never had: every step visible with its time,
 * reordering, and a duplicate.
 */
@Composable
fun RoutinesScreen(uid: String, routines: List<Routine>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val accent = accentOf(Accents.Task)

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Routine?>(null) }
    var actionsFor by remember { mutableStateOf<Routine?>(null) }
    var deleteAsk by remember { mutableStateOf<Routine?>(null) }

    fun runWithUndo(routine: Routine) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            try {
                val created = RoutineRepo.run(uid, routine)
                val result = snackbar.showSnackbar(
                    message = "${created.size} tasks added to today",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    RoutineRepo.undoRun(uid, created)
                }
            } catch (e: Exception) {
                notify(scope, snackbar, e.message ?: "Couldn't start that routine")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp
            )
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompactIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to tasks",
                        onClick = onBack
                    )
                    Spacer(Modifier.width(4.dp))
                    ScreenHeader(
                        title = "Routines",
                        subtitle = "Sets of tasks you drop into a day in one go",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (routines.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Bolt,
                        title = "No routines yet",
                        subtitle = "Group the steps you repeat, like your morning, " +
                            "then add the whole set to today with one tap."
                    )
                }
            }

            items(routines, key = { it.id }) { routine ->
                RoutineCard(
                    routine = routine,
                    accent = accent,
                    onRun = { runWithUndo(routine) },
                    onEdit = { editing = routine; editorOpen = true },
                    onMore = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actionsFor = routine
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
        AccentFab(
            icon = Icons.Rounded.Add,
            contentDescription = "New routine",
            onClick = { editing = null; editorOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 88.dp)
        )
    }

    actionsFor?.let { routine ->
        ActionSheet(
            title = routine.title,
            actions = listOf(
                SheetAction(Icons.Rounded.Edit, "Edit") {
                    editing = routine
                    editorOpen = true
                },
                SheetAction(Icons.Rounded.ContentCopy, "Duplicate") {
                    scope.launch {
                        runCatching {
                            RoutineRepo.save(
                                uid = uid,
                                editingId = null,
                                title = routine.title + " copy",
                                items = routine.items
                            )
                        }
                    }
                },
                SheetAction(Icons.Rounded.Delete, "Delete", destructive = true) {
                    deleteAsk = routine
                }
            ),
            onDismiss = { actionsFor = null }
        )
    }

    deleteAsk?.let { routine ->
        ConfirmDialog(
            title = "Delete this routine?",
            text = "“${routine.title}” will be gone for good. " +
                "Tasks it already added stay where they are.",
            confirmLabel = "Delete",
            onConfirm = {
                deleteAsk = null
                scope.launch { runCatching { RoutineRepo.delete(uid, routine.id) } }
            },
            onDismiss = { deleteAsk = null }
        )
    }

    if (editorOpen) {
        RoutineEditorSheet(
            uid = uid,
            existing = editing,
            onDismiss = { editorOpen = false }
        )
    }
}

/**
 * A routine, opened out. The chip on Tasks could only say a name and a count;
 * here the steps are the content, because the steps in order are what a
 * routine actually is.
 */
@Composable
private fun RoutineCard(
    routine: Routine,
    accent: Color,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timed = routine.items.mapNotNull { it.time.takeIf { t -> t.isNotBlank() } }.sorted()
    val span = when {
        timed.isEmpty() -> "No set times"
        timed.size == 1 -> "At ${timed.first()}"
        else -> "${timed.first()} to ${timed.last()}"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (routine.items.size == 1) "1 step"
                        else "${routine.items.size} steps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = span,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            CompactIconButton(
                icon = Icons.Rounded.MoreVert,
                contentDescription = "More for ${routine.title}",
                onClick = onMore
            )
        }

        // The steps themselves, in order, because the order is the routine.
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
            routine.items.take(5).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.time.isNotBlank()) {
                        Text(
                            text = item.time,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (routine.items.size > 5) {
                Text(
                    text = "and ${routine.items.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = "Add to today",
            icon = Icons.Rounded.PlayArrow,
            onClick = onRun,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
    }
}

/**
 * The editor. Steps carry their own time and can be moved, because a routine
 * without an order is just a pile of tasks.
 *
 * Reordering is a pair of arrows rather than a drag handle on purpose: a drag
 * gesture inside a scrolling sheet fights the scroll, and arrows say exactly
 * what they do.
 */
@Composable
fun RoutineEditorSheet(uid: String, existing: Routine?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    val items = remember {
        (existing?.items ?: listOf(RoutineItem("", "medium", emptyList(), emptyList(), "")))
            .toMutableStateList()
    }

    fun snapshot(): List<Any?> = listOf(title, items.toList())
    val original = remember { snapshot() }

    fun move(from: Int, to: Int) {
        if (to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
    }

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
        Text(
            "Give a step a time and it lands on that time. Leave it blank and it " +
                "goes into the next free half hour when you run the routine.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = item.title,
                        onValueChange = { items[index] = item.copy(title = it.take(60)) },
                        label = { Text("Step ${index + 1}") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    )
                    Column {
                        CompactIconButton(
                            icon = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Move step up",
                            onClick = { move(index, index - 1) }
                        )
                        CompactIconButton(
                            icon = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Move step down",
                            onClick = { move(index, index + 1) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeField(
                        value = item.time.takeIf { it.isNotBlank() }
                            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                        onValueChange = { picked ->
                            items[index] = item.copy(
                                time = picked?.let {
                                    String.format("%02d:%02d", it.hour, it.minute)
                                } ?: ""
                            )
                        },
                        label = "At (optional)",
                        modifier = Modifier.weight(1f)
                    )
                    CompactIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Remove step",
                        onClick = { if (items.size > 1) items.removeAt(index) }
                    )
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
