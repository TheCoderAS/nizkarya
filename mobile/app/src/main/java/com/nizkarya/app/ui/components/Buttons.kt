package com.nizkarya.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nizkarya.app.ui.theme.Motion
import com.nizkarya.app.ui.theme.ctaGradient
import com.nizkarya.app.ui.theme.onCta

// ─────────────────────────────────────────────────────────────────────────────
// The button hierarchy. One loud thing per screen (PrimaryCta), quiet
// everything else, and Cancel is always the quietest thing in sight so the
// eye lands on the action, never on the escape hatch.
// ─────────────────────────────────────────────────────────────────────────────

private val CtaShape = RoundedCornerShape(16.dp)

/** The one loud action on a screen: Save, Sign in, Start, Replan. */
@Composable
fun PrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 52.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = Motion.bouncy,
        label = "ctaPress"
    )
    val haptics = LocalHapticFeedback.current
    val fill = if (enabled) {
        ctaGradient()
    } else {
        SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    }
    val content = if (enabled) {
        onCta()
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Box(
        modifier = modifier
            .height(height)
            .scale(pressScale)
            .clip(CtaShape)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                maxLines = 1
            )
        }
    }
}

/** Supporting action: tonal, quieter than the CTA, louder than a ghost. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The quiet one. Every Cancel and every dismissive choice is a GhostButton,
 * deliberately colourless so it never competes with the action beside it.
 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (tint == Color.Unspecified) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                tint
            }
        ),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Destructive action: filled error red, never used for Cancel. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Gradient FAB. Material's FAB cannot take a Brush, hence the custom surface. */
@Composable
fun GradientFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = Motion.bouncy,
        label = "fabPress"
    )
    Box(
        modifier = modifier
            .scale(pressScale)
            .shadow(elevation = 6.dp, shape = CtaShape)
            .clip(CtaShape)
            .background(ctaGradient())
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .height(56.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onCta(),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = onCta())
        }
    }
}

/**
 * The one confirmation dialog shape: quiet Cancel on the left, coloured
 * action on the right, red when the action destroys something.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismissLabel: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            if (destructive) {
                DangerButton(confirmLabel, onClick = onConfirm)
            } else {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 44.dp)
                ) { Text(confirmLabel, style = MaterialTheme.typography.labelLarge) }
            }
        },
        dismissButton = { GhostButton(dismissLabel, onClick = onDismiss) }
    )
}
