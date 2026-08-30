package com.nizkarya.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nizkarya.app.ui.theme.accent

// Text input that belongs to this app rather than to Material's demo.
//
// OutlinedTextField draws a boxed outline with a label that floats up and
// notches the border. Stacked, as a form is, that is a lot of chrome: two of
// them per routine step turned four words of content into a screenful of
// boxes, and outlines fight the filled surfaces the rest of the app is built
// from. These fill instead of outline, put the label above rather than inside,
// and show focus as a ring in the section accent.

private val FieldShape = RoundedCornerShape(12.dp)

/** Shared anatomy, so a typed field and a tapped one cannot drift apart. */
@Composable
private fun FieldSurface(
    focused: Boolean,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val ring by animateColorAsState(
        targetValue = if (focused) accent() else Color.Transparent,
        label = "fieldFocus"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(FieldShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(width = 1.5.dp, color = ring, shape = FieldShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

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
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(accent()),
        interactionSource = interaction,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        modifier = modifier,
        decorationBox = { inner ->
            FieldSurface(focused = focused, minHeight = minHeight) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(8.dp))
                        trailing()
                    }
                }
            }
        }
    )
}

/**
 * A field you tap rather than type into: a date, a time, anything that opens a
 * picker. Same surface as [AppTextField], so a form does not visibly change
 * material halfway down.
 */
@Composable
fun TappableField(
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    minHeight: Dp = 46.dp
) {
    val scheme = MaterialTheme.colorScheme
    FieldSurface(
        focused = false,
        minHeight = minHeight,
        onClick = onClick,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1
            )
        }
    }
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
    minHeight: Dp = 46.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = singleLine,
            minHeight = minHeight,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            trailing = trailing,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The same label treatment above a picker, a chip row or a segmented choice. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * Small round button for a row's own action, most often a remove.
 *
 * A bare glyph floating next to a field reads as decoration and gives the
 * finger nothing to aim at. A filled circle says it is a button.
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

/**
 * Pick one of a few. Replaces Material's SingleChoiceSegmentedButtonRow, whose
 * outlined capsule with hairline dividers is the most recognisably stock
 * control Android offers and looked pasted in wherever it appeared.
 *
 * This is a filled track with the selection carried by an accent pill, which
 * is the same language as the navigation bar and the check rings.
 */
@Composable
fun SegmentedChoice(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = accent()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = selected == value
            val fill by animateColorAsState(
                targetValue = if (isSelected) {
                    accentColor.copy(alpha = 0.18f)
                } else {
                    Color.Transparent
                },
                label = "segmentFill"
            )
            val tint by animateColorAsState(
                targetValue = if (isSelected) {
                    accentColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "segmentTint"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(fill)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}
