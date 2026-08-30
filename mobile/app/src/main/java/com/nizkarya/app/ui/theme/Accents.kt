package com.nizkarya.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * One accent per section, so a glance at the colour tells you which tab you
 * are on before you read a single word.
 *
 * Each accent carries three values because one colour cannot do the job on
 * both grounds: [base] is the fill on the near-black canvas, [onLight] is the
 * same hue darkened enough to stay legible as text on a white one, and
 * [gradientEnd] is the second stop for the few places that earn a gradient.
 */
data class AccentSpec(
    val base: Color,
    val onLight: Color,
    val gradientEnd: Color
)

object Accents {
    /** Tasks and the day itself. The app's default voice. */
    val Task = AccentSpec(Color(0xFF7C6CFF), Color(0xFF5A45E0), Color(0xFF5B49E0))

    /** Habits, and "done" anywhere in the app. */
    val Habit = AccentSpec(Color(0xFF2ED3B7), Color(0xFF0E8A75), Color(0xFF12A98F))

    /** Streaks and progress. This is what replaced the fire emoji. */
    val Streak = AccentSpec(Color(0xFFFFB020), Color(0xFFA96A00), Color(0xFFF08A00))

    /** Overdue, the now line, and destructive actions. Nothing else. */
    val Late = AccentSpec(Color(0xFFFF5F6D), Color(0xFFCE2C3E), Color(0xFFE0364A))
}

/** The accent for the current section. Set once per tab, read by every child. */
val LocalAccent = compositionLocalOf { Accents.Task }

/** True when the app is currently painting on a light ground. */
@Composable
fun onLightGround(): Boolean = !MaterialTheme.colorScheme.background.isDarkColor()

/** The accent colour for [spec], picked for the ground it will sit on. */
@Composable
fun accentOf(spec: AccentSpec): Color =
    if (onLightGround()) spec.onLight else spec.base

/** The current section's accent. */
@Composable
fun accent(): Color = accentOf(LocalAccent.current)

/**
 * Text and icons that sit directly on a filled accent. Mint and amber are
 * bright enough that white on them fails contrast, so the ink flips.
 */
fun contentOn(fill: Color): Color =
    if (fill.relativeBrightness() > 0.60) Color(0xFF0A0A0F) else Color.White

private fun Color.relativeBrightness(): Double =
    0.299 * red + 0.587 * green + 0.114 * blue

private fun Color.isDarkColor(): Boolean = relativeBrightness() < 0.5

/** Motion constants shared by buttons, checks, and small bounces. */
object Motion {
    /** Springy settle for presses and check bounces. */
    val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Fixed brand hues. These deliberately do not come from the tonal scheme: the
 * same violet has to read the same in both themes for the app to look like
 * itself.
 */
object Brand {
    val Violet = Accents.Task.base
    val VioletBright = Color(0xFF9B8CFF)
    val Coral = Accents.Late.base
}

private val dynamicActive: Boolean
    @Composable get() = AppSettings.dynamicColor && supportsDynamicColor

/**
 * The loud fill: one primary action per screen, plus the add button. It
 * follows the section accent, so the button on Habits is mint and the button
 * on Tasks is violet without either screen asking for it.
 *
 * With wallpaper colours on, the gradient derives from the active scheme
 * instead, so Material You keeps working.
 */
@Composable
fun ctaGradient(): Brush {
    val scheme = MaterialTheme.colorScheme
    if (dynamicActive) {
        return Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))
    }
    val spec = LocalAccent.current
    return Brush.linearGradient(listOf(spec.base, spec.gradientEnd))
}

/** Content colour guaranteed readable on [ctaGradient]. */
@Composable
fun onCta(): Color =
    if (dynamicActive) MaterialTheme.colorScheme.onPrimary else contentOn(LocalAccent.current.base)

/**
 * The one big sweep, used on the day's progress figure and the sign-in
 * header. Two accents rather than three stops: this reads as a single object
 * lit from one side instead of a rainbow.
 */
@Composable
fun heroGradient(): Brush {
    val scheme = MaterialTheme.colorScheme
    return if (dynamicActive) {
        Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))
    } else {
        Brush.linearGradient(listOf(Accents.Task.base, Accents.Habit.base))
    }
}

/** Content colour guaranteed readable on [heroGradient]. */
@Composable
fun onHero(): Color =
    if (dynamicActive) MaterialTheme.colorScheme.onPrimary else Color.White
