package com.nizkarya.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nizkarya.app.data.Routine
import com.nizkarya.app.data.RoutineItem
import com.nizkarya.app.data.RoutineRepo
import com.nizkarya.app.ui.components.EmptyHint
import com.nizkarya.app.ui.components.ScreenHeader
import com.nizkarya.app.ui.components.toast
import kotlinx.coroutines.launch

@Composable
fun RoutinesScreen(uid: String, routines: List<Routine>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Routine?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        ScreenHeader(
            title = "Routines",
            subtitle = "Reusable task templates you can launch in one tap."
        )
        TextButton(onClick = { editing = null; editorOpen = true }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("New routine")
        }
        if (routines.isEmpty()) {
            EmptyHint("No routines yet. Create one to bundle repeatable steps.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(routines, key = { it.id }) { routine ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = routine.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${routine.items.size} step" +
                                        if (routine.items.size == 1) "" else "s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            RoutineRepo.run(uid, routine)
                                            toast(
                                                context,
                                                "${routine.items.size} tasks added to today."
                                            )
                                        } catch (e: Exception) {
                                            toast(context, e.message ?: "Unable to run routine")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Run routine",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { editing = routine; editorOpen = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit routine")
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            RoutineRepo.delete(uid, routine.id)
                                        } catch (e: Exception) {
                                            toast(context, e.message ?: "Unable to delete routine")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete routine")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (editorOpen) {
        RoutineEditorDialog(uid = uid, existing = editing, onDismiss = { editorOpen = false })
    }
}

@Composable
private fun RoutineEditorDialog(uid: String, existing: Routine?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    val items = remember {
        (existing?.items ?: listOf(RoutineItem("", "medium", emptyList(), emptyList(), "")))
            .toMutableStateList()
    }

    fun cyclePriority(priority: String): String = when (priority) {
        "low" -> "medium"
        "medium" -> "high"
        else -> "low"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New routine" else "Edit routine") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Routine title") },
                    singleLine = true,
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
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                items[index] = item.copy(priority = cyclePriority(item.priority))
                            }
                        ) {
                            Text(item.priority.replaceFirstChar { it.uppercase() })
                        }
                        IconButton(
                            onClick = { if (items.size > 1) items.removeAt(index) }
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove step")
                        }
                    }
                }
                TextButton(
                    onClick = {
                        items.add(RoutineItem("", "medium", emptyList(), emptyList(), ""))
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add step")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    val cleanItems = items
                        .map { it.copy(title = it.title.trim()) }
                        .filter { it.title.isNotEmpty() }
                    if (cleanTitle.isEmpty() || cleanItems.isEmpty()) {
                        toast(context, "Add a title and at least one step.")
                        return@Button
                    }
                    scope.launch {
                        try {
                            RoutineRepo.save(uid, existing?.id, cleanTitle, cleanItems)
                            onDismiss()
                        } catch (e: Exception) {
                            toast(context, e.message ?: "Unable to save routine")
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
