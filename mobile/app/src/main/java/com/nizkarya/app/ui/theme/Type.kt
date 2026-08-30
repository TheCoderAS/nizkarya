package com.nizkarya.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nizkarya.app.R

/**
 * Inter, bundled under res/font (SIL OFL, licence at mobile/InterFontLicense-OFL.txt).
 *
 * Roboto ships no SemiBold cut, which is why the weight scale kept swinging
 * between too thin (Normal) and sledgehammer (everything Bold): the
 * intermediate weights did not exist on device, so they were faked or rounded
 * away. Inter has real 500, 600 and 800 cuts, so the hierarchy below renders
 * as written.
 */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold)
)

private fun style(
    size: Double,
    lineHeight: Double,
    weight: FontWeight,
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = InterFamily,
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
 * The scale now opens at 40 instead of 30.
 *
 * The old one ran from 30 down to 12 in small steps, so a heading and a row
 * title differed by a couple of points and every screen read at one weight.
 * That flatness is what made a dense layout feel cluttered: nothing led, so
 * the eye had nowhere to land. The display tier exists for the counts that
 * matter (the day's progress, a consistency percentage) and is roughly three
 * times the size of the label under it. Rows themselves stay exactly as tight
 * as they were.
 */
val NizKaryaTypography = Typography(
    displayLarge = style(40.0, 42.0, FontWeight.ExtraBold, -1.4),
    displayMedium = style(34.0, 36.0, FontWeight.ExtraBold, -1.1),
    displaySmall = style(28.0, 32.0, FontWeight.ExtraBold, -0.8),
    headlineLarge = style(26.0, 30.0, FontWeight.Bold, -0.6),
    headlineMedium = style(22.0, 26.0, FontWeight.Bold, -0.4),
    headlineSmall = style(19.0, 24.0, FontWeight.Bold, -0.3),
    titleLarge = style(19.0, 24.0, FontWeight.SemiBold, -0.3),
    titleMedium = style(15.5, 20.0, FontWeight.SemiBold, -0.1),
    titleSmall = style(13.0, 17.0, FontWeight.Bold, 0.1),
    bodyLarge = style(15.0, 20.0, FontWeight.Medium, -0.05),
    bodyMedium = style(13.5, 18.0, FontWeight.Medium, 0.0),
    bodySmall = style(12.0, 16.0, FontWeight.Medium, 0.05),
    labelLarge = style(13.0, 17.0, FontWeight.SemiBold, 0.1),
    labelMedium = style(11.5, 14.0, FontWeight.SemiBold, 0.2),
    labelSmall = style(10.5, 13.0, FontWeight.Bold, 0.5)
)

/** Rounded, but tight. Big radii read as wasted space. */
val NizKaryaShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp)
)
