@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val dateLabel = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.toLocalDateFromPicker(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Tappable date field that opens the Material 3 calendar picker.
 * Nobody types "2026-01-31" into a native app.
 */
@Composable
fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String = "Date",
    modifier: Modifier = Modifier,
    allowClear: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        TappableField(
            value = value?.format(dateLabel),
            placeholder = "Not scheduled",
            icon = Icons.Rounded.CalendarMonth,
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now()).toPickerMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(state.selectedDateMillis?.toLocalDateFromPicker())
                        showPicker = false
                    }
                ) { Text("Set date", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                Row {
                    if (allowClear) {
                        GhostButton(
                            text = "Clear",
                            onClick = {
                                onValueChange(null)
                                showPicker = false
                            }
                        )
                    }
                    GhostButton(text = "Cancel", onClick = { showPicker = false })
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/** Tappable time field that opens the Material 3 clock picker. */
@Composable
fun TimeField(
    value: LocalTime?,
    onValueChange: (LocalTime?) -> Unit,
    label: String = "Time",
    modifier: Modifier = Modifier,
    allowClear: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        TappableField(
            value = value?.let { String.format("%02d:%02d", it.hour, it.minute) },
            placeholder = "None",
            icon = Icons.Rounded.Schedule,
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showPicker) {
        val initial = value ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(LocalTime.of(state.hour, state.minute))
                        showPicker = false
                    }
                ) { Text("Set time", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                Row {
                    if (allowClear) {
                        GhostButton(
                            text = "Clear",
                            onClick = {
                                onValueChange(null)
                                showPicker = false
                            }
                        )
                    }
                    GhostButton(text = "Cancel", onClick = { showPicker = false })
                }
            },
            title = { Text("Pick a time") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = state)
                }
            }
        )
    }
}

/**
 * Time as a small chip rather than a full text field.
 *
 * [TimeField] is a labelled field on its own line, which is right when a time
 * owns a line of a form. In a dense row, where the time sits beside a title
 * field, a drag handle and a remove button, that anatomy is too tall and too
 * wide, and it would force the row taller than the reordering maths wants it.
 */
@Composable
fun CompactTimeField(
    value: LocalTime?,
    onValueChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Any time"
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { showPicker = true }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: placeholder,
            style = MaterialTheme.typography.labelLarge,
            color = if (value == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
    }

    if (showPicker) {
        val initial = value ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(LocalTime.of(state.hour, state.minute))
                        showPicker = false
                    }
                ) { Text("Set time", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                Row {
                    GhostButton(
                        text = "Clear",
                        onClick = {
                            onValueChange(null)
                            showPicker = false
                        }
                    )
                    GhostButton(text = "Cancel", onClick = { showPicker = false })
                }
            },
            title = { Text("Pick a time") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = state)
                }
            }
        )
    }
}

/**
 * Standard editor sheet: title, scrollable body, and a pinned Cancel/Save
 * footer.
 *
 * When [dirty] is set, every way out (swipe down, scrim tap, back, the
 * Cancel button) is vetoed before the sheet moves and the discard dialog
 * appears on top of the still-open sheet. The previous version let the sheet
 * animate closed and then re-opened it, which read as a glitch.
 */
@Composable
fun EditorSheet(
    title: String,
    confirmLabel: String = "Save",
    dirty: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    val currentDirty by rememberUpdatedState(dirty)
    var askDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && currentDirty) {
                askDiscard = true
                false
            } else {
                true
            }
        }
    )

    if (askDiscard) {
        ConfirmDialog(
            title = "Discard your changes?",
            text = "You have edits here that have not been saved yet.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onConfirm = {
                askDiscard = false
                onDismiss()
            },
            onDismiss = {
                askDiscard = false
                // Belt and braces: if a back path slipped past the veto and
                // started hiding the sheet, bring it back.
                scope.launch { sheetState.show() }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (currentDirty) askDiscard = true else onDismiss() },
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                content()
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(
                    text = "Cancel",
                    onClick = { if (currentDirty) askDiscard = true else onDismiss() },
                    modifier = Modifier.weight(1f)
                )
                PrimaryCta(
                    text = confirmLabel,
                    onClick = onConfirm,
                    height = 48.dp,
                    modifier = Modifier.weight(1.7f)
                )
            }
        }
    }
}

@Composable
fun FieldSpacer() = Spacer(Modifier.height(2.dp))

@Composable
fun HSpace(width: Int) = Spacer(Modifier.width(width.dp))
