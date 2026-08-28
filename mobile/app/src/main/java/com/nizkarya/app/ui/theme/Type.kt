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

private fun style(
    size: Double,
    lineHeight: Double,
    weight: FontWeight,
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

/**
 * Denser and heavier than the M3 baseline.
 *
 * Weights sit on Roboto's real cuts wherever it matters. Roboto ships
 * Regular (400), Medium (500), Bold (700) and Black (900); 600 is not one of
 * them, so a SemiBold either gets synthesised or rounded down to Medium
 * depending on the device. That is why the earlier pass still read thin on
 * hardware while looking heavier in the values. Body text is Bold and
 * everything above it is ExtraBold, both of which land on a genuine cut.
 */
val NizKaryaTypography = Typography(
    displaySmall = style(30.0, 36.0, FontWeight.ExtraBold, -0.5),
    headlineLarge = style(27.0, 32.0, FontWeight.ExtraBold, -0.4),
    headlineMedium = style(23.0, 28.0, FontWeight.ExtraBold, -0.3),
    headlineSmall = style(20.0, 25.0, FontWeight.ExtraBold, -0.2),
    titleLarge = style(18.0, 23.0, FontWeight.ExtraBold, -0.2),
    titleMedium = style(15.0, 20.0, FontWeight.Bold, 0.0),
    titleSmall = style(13.0, 18.0, FontWeight.Bold, 0.05),
    bodyLarge = style(15.0, 20.0, FontWeight.Bold, 0.0),
    bodyMedium = style(13.5, 18.0, FontWeight.Bold, 0.05),
    // Secondary metadata stays a step down, or a row has no hierarchy left.
    bodySmall = style(12.0, 16.0, FontWeight.Medium, 0.1),
    labelLarge = style(13.0, 17.0, FontWeight.Bold, 0.1),
    labelMedium = style(11.0, 14.0, FontWeight.Bold, 0.4),
    labelSmall = style(10.0, 13.0, FontWeight.Bold, 0.4)
)

/** Rounded, but tighter than before. Big radii read as wasted space. */
val NizKaryaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
