package com.nizkarya.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nizkarya.app.ui.theme.AccentSpec
import com.nizkarya.app.ui.theme.Motion
import com.nizkarya.app.ui.theme.accentOf

/** One destination in the bottom pill. */
data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val accent: AccentSpec
)

/**
 * Floating pill navigation.
 *
 * Material's NavigationBar is a full-bleed slab pinned to the bottom edge,
 * which is the single most generic thing an Android app can wear. This floats
 * clear of the edge and colours the selected item in that tab's own accent,
 * so the bar itself tells you which section you are in.
 */
@Composable
fun NavPill(
    items: List<NavItem>,
    currentRoute: String,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                NavPillItem(
                    item = item,
                    selected = currentRoute == item.route,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun NavPillItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val accent = accentOf(item.accent)
    val tint by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navTint"
    )
    val pill by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.15f) else Color.Transparent,
        label = "navPill"
    )
    val pop by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = Motion.bouncy,
        label = "navPop"
    )
    // No ripple: the pill and the little pop already answer the tap, and a
    // ripple spilling out of a rounded chip looks broken.
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(pill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(21.dp).scale(pop)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}

/** Height the pill occupies, so scrolling content can clear it. */
val NavPillHeight = 78.dp
