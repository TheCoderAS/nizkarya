package com.nizkarya.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Fixed brand hues for the gradients. These deliberately do not come from the
 * tonal scheme: the same violet works on both light and dark surfaces, which
 * is what lets the brand look identical in both themes.
 */
object Brand {
    val Violet = Color(0xFF6741D9)
    val VioletBright = Color(0xFF8B5CF6)
    val Coral = Color(0xFFF97362)
}

/** Motion constants shared by buttons, checks, and small bounces. */
object Motion {
    /** Springy settle for presses and check bounces. */
    val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
}

private val dynamicActive: Boolean
    @Composable get() = AppSettings.dynamicColor && supportsDynamicColor

/**
 * The loud fill: one primary action per screen, plus the FAB. When the
 * wallpaper-colour switch is on, the gradient derives from the active scheme
 * instead so dynamic themes keep working.
 */
@Composable
fun ctaGradient(): Brush {
    val scheme = MaterialTheme.colorScheme
    return if (dynamicActive) {
        Brush.linearGradient(listOf(scheme.primary, lerp(scheme.primary, scheme.tertiary, 0.55f)))
    } else {
        Brush.linearGradient(listOf(Brand.Violet, Brand.VioletBright))
    }
}

/** Content colour that is guaranteed readable on [ctaGradient]. */
@Composable
fun onCta(): Color =
    if (dynamicActive) MaterialTheme.colorScheme.onPrimary else Color.White

/** Violet-to-coral sweep for the Today hero card and the Auth header. */
@Composable
fun heroGradient(): Brush {
    val scheme = MaterialTheme.colorScheme
    return if (dynamicActive) {
        Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))
    } else {
        Brush.linearGradient(listOf(Brand.Violet, Color(0xFF9A4FD3), Brand.Coral))
    }
}

/** Content colour that is guaranteed readable on [heroGradient]. */
@Composable
fun onHero(): Color =
    if (dynamicActive) MaterialTheme.colorScheme.onPrimary else Color.White
