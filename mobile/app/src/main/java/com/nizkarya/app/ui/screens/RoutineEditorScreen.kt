package com.nizkarya.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nizkarya.app.data.Routine
import com.nizkarya.app.data.RoutineItem
import com.nizkarya.app.data.RoutineRepo
import com.nizkarya.app.ui.components.AppTextField
import com.nizkarya.app.ui.components.CompactIconButton
import com.nizkarya.app.ui.components.CompactTimeField
import com.nizkarya.app.ui.components.ConfirmDialog
import com.nizkarya.app.ui.components.GhostButton
import com.nizkarya.app.ui.components.IconAction
import com.nizkarya.app.ui.components.LabelledField
import com.nizkarya.app.ui.components.LocalSnackbar
import com.nizkarya.app.ui.components.PrimaryCta
import com.nizkarya.app.ui.components.SecondaryButton
import com.nizkarya.app.ui.components.notify
import com.nizkarya.app.ui.theme.Accents
import com.nizkarya.app.ui.theme.accentOf
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Every step row is exactly this tall, and the gap between rows is fixed too.
 *
 * That uniformity is load-bearing. It lets the drag work out where a step has
 * been dropped with one division instead of hit-testing the list's layout
 * every frame, which is both simpler and exact: no jitter at the boundaries,
 * no dependence on what happens to be measured and visible.
 */
private val StepRowHeight = 60.dp
private val StepGap = 8.dp

/**
 * Editing a routine, full screen.
 *
 * This was a bottom sheet. A sheet claims vertical drags for its own
 * dismissal, so a drag handle inside one is always fighting the container it
 * sits in, and the list of steps was capped at a scroll box a few rows tall.
 * A routine is an ordered list, so it gets a screen where the ordering can
 * actually be done.
 */
@Composable
fun RoutineEditorScreen(
    uid: String,
    existing: Routine?,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    val items = remember {
        (existing?.items ?: listOf(RoutineItem("", "medium", emptyList(), emptyList(), "")))
            .toMutableStateList()
    }
    var askDiscard by remember { mutableStateOf(false) }

    fun snapshot(): List<Any?> = listOf(title, items.toList())
    val original = remember { snapshot() }
    val dirty = snapshot() != original

    // Drag state. dragIndex is where the held step currently sits, dragOffset
    // is how far the finger has moved past that slot.
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val slotPx = with(density) { (StepRowHeight + StepGap).toPx() }

    fun move(from: Int, to: Int) {
        if (to !in items.indices || from == to) return
        items.add(to, items.removeAt(from))
    }

    fun leave() {
        if (dirty) askDiscard = true else onDone()
    }

    BackHandler(enabled = dirty) { askDiscard = true }

    fun save() {
        val cleanTitle = title.trim()
        val cleanItems = items.map { it.copy(title = it.title.trim()) }
            .filter { it.title.isNotEmpty() }
        if (cleanTitle.isEmpty() || cleanItems.isEmpty()) {
            notify(scope, snackbar, "Add a name and at least one step.")
            return
        }
        scope.launch {
            try {
                RoutineRepo.save(uid, existing?.id, cleanTitle, cleanItems)
                onDone()
            } catch (e: Exception) {
                notify(scope, snackbar, e.message ?: "Couldn't save that routine")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to routines",
                onClick = { leave() }
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (existing == null) "New routine" else "Edit routine",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(StepGap)
        ) {
            item(key = "name") {
                LabelledField(
                    label = "Routine name",
                    value = title,
                    onValueChange = { title = it.take(60) },
                    placeholder = "Morning block",
                    minHeight = 50.dp
                )
            }
            item(key = "steps-label") {
                Column {
                    Text("Steps", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Drag the handle to reorder. Give a step a time and it lands " +
                            "on that time; leave it blank and it goes into the next free " +
                            "half hour when you run the routine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(items, key = { index, _ -> "step-$index" }) { index, item ->
                val held = dragIndex == index
                StepRow(
                    index = index,
                    item = item,
                    held = held,
                    canRemove = items.size > 1,
                    onTitleChange = { items[index] = item.copy(title = it.take(60)) },
                    onTimeChange = { picked ->
                        items[index] = item.copy(
                            time = picked?.let {
                                String.format("%02d:%02d", it.hour, it.minute)
                            } ?: ""
                        )
                    },
                    onRemove = { if (items.size > 1) items.removeAt(index) },
                    modifier = Modifier
                        .zIndex(if (held) 1f else 0f)
                        .graphicsLayer { translationY = if (held) dragOffset else 0f },
                    dragModifier = Modifier.pointerInput(items.size) {
                        detectDragGestures(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val from = dragIndex
                                if (from != null) {
                                    dragOffset += amount.y
                                    // One slot of travel is one position.
                                    // Moving the item and taking that slot back
                                    // out of the offset keeps the held row
                                    // sitting under the finger.
                                    val shift = (dragOffset / slotPx).roundToInt()
                                    val to = (from + shift).coerceIn(0, items.lastIndex)
                                    if (to != from) {
                                        move(from, to)
                                        dragOffset -= (to - from) * slotPx
                                        dragIndex = to
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove
                                        )
                                    }
                                }
                            },
                            onDragEnd = { dragIndex = null; dragOffset = 0f },
                            onDragCancel = { dragIndex = null; dragOffset = 0f }
                        )
                    }
                )
            }

            item(key = "add-step") {
                SecondaryButton(
                    text = "Add step",
                    icon = Icons.Rounded.Add,
                    onClick = {
                        items.add(RoutineItem("", "medium", emptyList(), emptyList(), ""))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GhostButton(text = "Cancel", onClick = { leave() }, modifier = Modifier.weight(1f))
            PrimaryCta(
                text = "Save",
                onClick = { save() },
                height = 48.dp,
                modifier = Modifier.weight(1.7f)
            )
        }
    }

    if (askDiscard) {
        ConfirmDialog(
            title = "Discard your changes?",
            text = "You have edits here that have not been saved yet.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onConfirm = {
                askDiscard = false
                onDone()
            },
            onDismiss = { askDiscard = false }
        )
    }
}

/**
 * One step. Fixed height, because [StepRowHeight] is what the drag maths
 * counts in: title, time and the two buttons all share one line so the row
 * stays short enough that several are on screen at once while reordering.
 */
@Composable
private fun StepRow(
    index: Int,
    item: RoutineItem,
    held: Boolean,
    canRemove: Boolean,
    onTitleChange: (String) -> Unit,
    onTimeChange: (LocalTime?) -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(StepRowHeight)
            .then(
                if (held) {
                    Modifier.shadow(10.dp, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (held) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            )
            .padding(start = 2.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = dragModifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Reorder step ${index + 1}",
                tint = if (held) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }
        AppTextField(
            value = item.title,
            onValueChange = onTitleChange,
            placeholder = "Step ${index + 1}",
            minHeight = 44.dp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        CompactTimeField(
            value = item.time.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            onValueChange = onTimeChange,
            placeholder = "Any"
        )
        Spacer(Modifier.width(6.dp))
        IconAction(
            icon = Icons.Rounded.Close,
            contentDescription = "Remove step ${index + 1}",
            onClick = { if (canRemove) onRemove() },
            tone = if (canRemove) accentOf(Accents.Late) else Color.Unspecified,
            diameter = 30.dp
        )
    }
}
