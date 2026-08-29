@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.nizkarya.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Routine
import com.nizkarya.app.data.RoutineItem
import com.nizkarya.app.data.RoutineRepo
import com.nizkarya.app.ui.components.CompactRow
import com.nizkarya.app.ui.components.ActionSheet
import com.nizkarya.app.ui.components.ConfirmDialog
import com.nizkarya.app.ui.components.EditorSheet
import com.nizkarya.app.ui.components.GradientFab
import com.nizkarya.app.ui.components.PrimaryCta
import com.nizkarya.app.ui.components.SecondaryButton
import com.nizkarya.app.ui.components.SheetAction
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.notify
import kotlinx.coroutines.launch

@Composable
fun RoutinesScreen(uid: String, routines: List<Routine>) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Routine?>(null) }
    var actionsFor by remember { mutableStateOf<Routine?>(null) }
    var deleteAsk by remember { mutableStateOf<Routine?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            GradientFab(
                text = "New routine",
                icon = Icons.Rounded.Add,
                onClick = { editing = null; editorOpen = true }
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (routines.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Bolt,
                    title = "No routines yet",
                    subtitle = "Group the steps you do over and over, like your morning, " +
                        "then add them all to today in one tap."
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(routines, key = { it.id }) { routine ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier
                                .animateItem()
                                .clip(MaterialTheme.shapes.medium)
                                .combinedClickable(
                                    onClick = { editing = routine; editorOpen = true },
                                    onLongClick = {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                        actionsFor = routine
                                    }
                                )
                        ) {
                            Column {
                                CompactRow {
                                    Text(
                                        routine.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = routine.items.take(3)
                                            .joinToString(" · ") { it.title }
                                            .ifBlank { "No steps yet" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                PrimaryCta(
                                    text = "Start routine",
                                    icon = Icons.Rounded.PlayArrow,
                                    height = 44.dp,
                                    onClick = {
                                        scope.launch {
                                            try {
                                                RoutineRepo.run(uid, routine)
                                                notify(
                                                    scope, snackbar,
                                                    "${routine.items.size} tasks added to today"
                                                )
                                            } catch (e: Exception) {
                                                notify(
                                                    scope, snackbar,
                                                    e.message ?: "Couldn't start that routine"
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    actionsFor?.let { routine ->
        ActionSheet(
            title = routine.title,
            actions = listOf(
                SheetAction(Icons.Rounded.Edit, "Edit") {
                    editing = routine
                    editorOpen = true
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
            text = "“${routine.title}” will be gone for good. Tasks it already added stay.",
            confirmLabel = "Delete",
            onConfirm = {
                deleteAsk = null
                scope.launch { runCatching { RoutineRepo.delete(uid, routine.id) } }
            },
            onDismiss = { deleteAsk = null }
        )
    }

    if (editorOpen) {
        RoutineEditorSheet(uid = uid, existing = editing, onDismiss = { editorOpen = false })
    }
}

@Composable
private fun RoutineEditorSheet(uid: String, existing: Routine?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    val items = remember {
        (existing?.items ?: listOf(RoutineItem("", "medium", emptyList(), emptyList(), "")))
            .toMutableStateList()
    }

    fun snapshot(): List<Any?> = listOf(title, items.toList())
    val original = remember { snapshot() }

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
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.title,
                    onValueChange = { items[index] = item.copy(title = it.take(60)) },
                    label = { Text("Step ${index + 1}") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { if (items.size > 1) items.removeAt(index) },
                    enabled = items.size > 1
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove step")
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
