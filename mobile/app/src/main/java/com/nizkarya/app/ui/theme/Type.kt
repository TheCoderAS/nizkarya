package com.nizkarya.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val default = Typography()

private fun TextStyle.tuned(
    weight: FontWeight = fontWeight ?: FontWeight.Normal,
    tracking: Double = letterSpacing.value.toDouble()
) = copy(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

/**
 * Slightly tighter, more confident type than the M3 baseline: display and
 * headline weights are raised so headers feel deliberate rather than default.
 */
val NizKaryaTypography = Typography(
    displaySmall = default.displaySmall.tuned(FontWeight.Bold, -0.5),
    headlineLarge = default.headlineLarge.tuned(FontWeight.Bold, -0.4),
    headlineMedium = default.headlineMedium.tuned(FontWeight.Bold, -0.3),
    headlineSmall = default.headlineSmall.tuned(FontWeight.SemiBold, -0.2),
    titleLarge = default.titleLarge.tuned(FontWeight.SemiBold, -0.2),
    titleMedium = default.titleMedium.tuned(FontWeight.SemiBold, 0.0),
    titleSmall = default.titleSmall.tuned(FontWeight.SemiBold, 0.1),
    bodyLarge = default.bodyLarge.tuned(FontWeight.Normal, 0.1),
    bodyMedium = default.bodyMedium.tuned(FontWeight.Normal, 0.15),
    bodySmall = default.bodySmall.tuned(FontWeight.Normal, 0.2),
    labelLarge = default.labelLarge.tuned(FontWeight.SemiBold, 0.1),
    labelMedium = default.labelMedium.tuned(FontWeight.Medium, 0.4),
    labelSmall = default.labelSmall.tuned(FontWeight.Medium, 0.4)
)

/** Rounded, friendly shapes — closer to modern Google apps than M3 defaults. */
val NizKaryaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
