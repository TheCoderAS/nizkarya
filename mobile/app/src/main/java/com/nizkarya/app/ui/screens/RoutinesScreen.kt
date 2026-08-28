@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Routine
import com.nizkarya.app.data.RoutineItem
import com.nizkarya.app.data.RoutineRepo
import com.nizkarya.app.ui.components.EditorSheet
import com.nizkarya.app.ui.components.EmptyState
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.notify
import kotlinx.coroutines.launch

@Composable
fun RoutinesScreen(uid: String, routines: List<Routine>) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Routine?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; editorOpen = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New routine") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (routines.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Bolt,
                    title = "No routines yet",
                    subtitle = "Bundle steps you repeat — your morning, a workout — " +
                        "then start them all in one tap."
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(routines, key = { it.id }) { routine ->
                        var menuOpen by remember { mutableStateOf(false) }
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column {
                                ListItem(
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    ),
                                    headlineContent = {
                                        Text(
                                            routine.title,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = routine.items.take(3)
                                                .joinToString(" · ") { it.title }
                                                .ifBlank { "No steps yet" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    },
                                    trailingContent = {
                                        Box {
                                            IconButton(onClick = { menuOpen = true }) {
                                                Icon(
                                                    Icons.Outlined.MoreVert,
                                                    contentDescription = "More"
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = menuOpen,
                                                onDismissRequest = { menuOpen = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit") },
                                                    onClick = {
                                                        menuOpen = false
                                                        editing = routine
                                                        editorOpen = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete") },
                                                    onClick = {
                                                        menuOpen = false
                                                        scope.launch {
                                                            runCatching {
                                                                RoutineRepo.delete(
                                                                    uid, routine.id
                                                                )
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                )
                                FilledTonalButton(
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
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text("Start routine")
                                }
                            }
                        }
                    }
                }
            }
        }
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

    EditorSheet(
        title = if (existing == null) "New routine" else "Edit routine",
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
                    Icon(Icons.Outlined.Close, contentDescription = "Remove step")
                }
            }
        }
        FilledTonalButton(
            onClick = { items.add(RoutineItem("", "medium", emptyList(), emptyList(), "")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("Add step")
        }
        Spacer(Modifier.height(4.dp))
    }
}
