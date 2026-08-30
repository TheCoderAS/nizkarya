package com.nizkarya.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nizkarya.app.ui.theme.accent

// Text input that belongs to this app rather than to Material's demo.
//
// OutlinedTextField draws a boxed outline with a label that floats up and
// notches the border. Stacked, as a form is, that is a lot of chrome: on the
// routine editor two of them per step turned four words of content into a
// screenful of boxes, and the outlines fought the filled surfaces the rest of
// the app is built from. These fill instead of outline, put the label above
// rather than inside, and show focus as a ring in the section accent.

private val FieldShape = RoundedCornerShape(12.dp)

/**
 * Single or multi line text input on a filled surface.
 *
 * [placeholder] sits inside the field until you type, so an empty form reads
 * as a list of prompts rather than a list of boxes with labels stuck to them.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = 46.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring by animateColorAsState(
        targetValue = if (focused) accent() else Color.Transparent,
        label = "fieldFocus"
    )
    val scheme = MaterialTheme.colorScheme

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(accent()),
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(FieldShape)
            .background(scheme.surfaceContainerHigh)
            .border(width = 1.5.dp, color = ring, shape = FieldShape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = scheme.onSurfaceVariant
                    )
                }
                inner()
            }
        }
    )
}

/** A field with its label above it, which is where a label can be read. */
@Composable
fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = 46.dp
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = singleLine,
            minHeight = minHeight,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Small round button for a row's own action, most often a remove.
 *
 * A bare glyph floating next to a field, which is what the routine editor had,
 * reads as decoration and gives the finger nothing to aim at. A filled circle
 * says it is a button and gives it edges.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = Color.Unspecified,
    diameter: Dp = 32.dp
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (tone == Color.Unspecified) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                tone
            },
            modifier = Modifier.size(diameter * 0.52f)
        )
    }
}
