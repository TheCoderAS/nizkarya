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
 * away. Inter has real 500 and 600 cuts, so the hierarchy below finally
 * renders as written.
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
 * Same compact sizes as before (that density was fought for); only the
 * weights change. Headlines 700, titles and labels 600, body 500: solid
 * without shouting, and every step is a real font file.
 */
val NizKaryaTypography = Typography(
    displaySmall = style(30.0, 36.0, FontWeight.Bold, -0.5),
    headlineLarge = style(27.0, 32.0, FontWeight.Bold, -0.4),
    headlineMedium = style(23.0, 28.0, FontWeight.Bold, -0.3),
    headlineSmall = style(20.0, 25.0, FontWeight.Bold, -0.2),
    titleLarge = style(18.0, 23.0, FontWeight.SemiBold, -0.2),
    titleMedium = style(15.0, 20.0, FontWeight.SemiBold, 0.0),
    titleSmall = style(13.0, 18.0, FontWeight.SemiBold, 0.05),
    bodyLarge = style(15.0, 20.0, FontWeight.Medium, 0.0),
    bodyMedium = style(13.5, 18.0, FontWeight.Medium, 0.05),
    bodySmall = style(12.0, 16.0, FontWeight.Medium, 0.1),
    labelLarge = style(13.0, 17.0, FontWeight.SemiBold, 0.1),
    labelMedium = style(11.0, 14.0, FontWeight.SemiBold, 0.3),
    labelSmall = style(10.0, 13.0, FontWeight.SemiBold, 0.3)
)

/** Rounded, but tight. Big radii read as wasted space. */
val NizKaryaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
