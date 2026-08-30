package com.nizkarya.app.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * The widget palette.
 *
 * Widgets draw through RemoteViews, which means no bundled Inter, no gradient
 * brushes and no Canvas. So these are the app's accents as flat colours, and
 * the rounded surfaces come from shape drawables rather than a corner
 * modifier, which is also the only way to get them below API 31. The widgets
 * are cousins of the app's look rather than a pixel match, and pretending
 * otherwise would just produce something that looked broken next to it.
 *
 * A widget also sits on the user's wallpaper rather than on our canvas, so it
 * commits to the dark surface in both themes instead of following the system.
 * A translucent light card over a dark wallpaper is unreadable, and the app's
 * own near-black already reads as deliberate on anything.
 */
object WidgetLook {
    val Text = ColorProvider(Color(0xFFF3F3F8))
    val Dim = ColorProvider(Color(0xFF9A9AAE))
    val Task = ColorProvider(Color(0xFF7C6CFF))
    val Habit = ColorProvider(Color(0xFF2ED3B7))
    val Streak = ColorProvider(Color(0xFFFFB020))
    val Late = ColorProvider(Color(0xFFFF5F6D))
}
