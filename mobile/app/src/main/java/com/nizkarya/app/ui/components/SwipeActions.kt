@file:OptIn(ExperimentalMaterial3Api::class)

package com.nizkarya.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Swipe container for list rows: start-to-end completes (green sweep with a
 * check), end-to-start archives. The row always settles back rather than
 * dismissing, because the data change is what removes or restyles it; letting
 * the box dismiss too would fight the list animation.
 */
@Composable
fun SwipeableRow(
    onComplete: () -> Unit,
    onArchive: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val currentComplete by rememberUpdatedState(onComplete)
    val currentArchive by rememberUpdatedState(onArchive)

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentComplete()
                SwipeToDismissBoxValue.EndToStart -> currentArchive?.invoke()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f }
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = onArchive != null,
        backgroundContent = {
            val completing = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val archiving = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val background = when {
                completing -> successColor()
                archiving -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(background)
                    .padding(horizontal = 22.dp),
                contentAlignment = if (completing) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                when {
                    completing -> Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    archiving -> Icon(
                        imageVector = Icons.Rounded.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        content = { content() }
    )
}
