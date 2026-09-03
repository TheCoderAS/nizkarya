@file:OptIn(ExperimentalFoundationApi::class)

package com.nizkarya.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// The timeline is what makes Today read as a day instead of a list. Three
// columns: a narrow time rail, a thread with a node on it, and the block
// itself. Empty hours collapse rather than being drawn to scale, because a
// day holding a 09:00 and an 18:00 would otherwise be mostly blank space.

private val RailWidth = 42.dp
private val ThreadWidth = 16.dp
private val ThreadInset = 7.dp
private val NodeTop = 15.dp

/** Where a row sits in the thread, so the line stops at the two ends. */
enum class ThreadShape { First, Middle, Last, Only }

/**
 * One entry on the timeline: the time on the left, a node on the thread, and
 * whatever [content] the caller draws to the right of it.
 */
@Composable
fun TimelineRow(
    time: String?,
    nodeColor: Color,
    filled: Boolean,
    shape: ThreadShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val drawsUp = shape == ThreadShape.Middle || shape == ThreadShape.Last
    val drawsDown = shape == ThreadShape.Middle || shape == ThreadShape.First

    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            text = time.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(RailWidth).padding(top = NodeTop, end = 6.dp)
        )
        Box(modifier = Modifier.width(ThreadWidth).fillMaxHeight()) {
            if (drawsUp) {
                Box(
                    modifier = Modifier
                        .padding(start = ThreadInset)
                        .width(1.dp)
                        .height(NodeTop)
                        .background(line)
                )
            }
            if (drawsDown) {
                Box(
                    modifier = Modifier
                        .padding(start = ThreadInset, top = NodeTop + 9.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(line)
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 3.dp, top = NodeTop)
                    .size(9.dp)
                    .then(
                        if (filled) {
                            Modifier.background(nodeColor, CircleShape)
                        } else {
                            Modifier
                                .background(MaterialTheme.colorScheme.background, CircleShape)
                                .border(1.5.dp, nodeColor, CircleShape)
                        }
                    )
            )
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/**
 * The block itself: a card with a coloured leading edge. That edge is the
 * whole legend. Violet is a task, mint a habit, coral something late, and no
 * badge has to spell it out.
 */
@Composable
fun TimelineBlock(
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    // The bar is a sibling of everything else rather than a member of the top
    // row, so it runs the height of the block once [below] opens something out
    // underneath. Its own vertical padding still centres it against a
    // collapsed block, which is every block that has nothing to expand.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .width(3.dp)
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leading != null) leading() else Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    content = content
                )
                trailing?.invoke()
                Spacer(Modifier.width(10.dp))
            }
            below?.invoke()
        }
    }
}

/**
 * Where you are in the day. This is the first thing the eye should find on
 * Today, so it is the only coral on screen when nothing is overdue.
 */
@Composable
fun NowLine(label: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(RailWidth).padding(end = 6.dp)
        )
        Box(modifier = Modifier.width(ThreadWidth)) {
            Box(
                modifier = Modifier
                    .padding(start = 3.dp)
                    .size(9.dp)
                    .background(color, CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.5.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color.copy(alpha = 0.5f))
        )
    }
}

/**
 * Divider between the timed part of the day and everything without a time.
 * A label and a hairline, not a card: this names a section, it does not
 * compete with the content under it.
 */
@Composable
fun TimelineDivider(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}
