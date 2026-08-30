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
fun RoutinesScreen(
    uid: String,
    routines: List<Routine>,
    onBack: () -> Unit,
    onEditRoutine: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val accent = accentOf(Accents.Task)

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
                    onEdit = { onEditRoutine(routine.id) },
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
            onClick = { onEditRoutine(null) },
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
                SheetAction(Icons.Rounded.Edit, "Edit") { onEditRoutine(routine.id) },
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
