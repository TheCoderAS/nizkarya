package com.nizkarya.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** True when this device can source colours from the user's wallpaper. */
val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

// The Material roles are mapped straight onto the four accents: primary is
// violet, tertiary is amber (streaks), error is coral, and the surface tiers
// walk up from the near-black canvas in small steps. Anything reading
// MaterialTheme.colorScheme therefore lands on the same palette as anything
// reading LocalAccent.

private val BrandDarkColors = darkColorScheme(
    primary = VioletBase,
    onPrimary = PureWhite,
    primaryContainer = VioletTintDark,
    onPrimaryContainer = VioletTintLight,
    inversePrimary = VioletDeep,

    secondary = MintBase,
    onSecondary = MintInk,
    secondaryContainer = MintTintDark,
    onSecondaryContainer = MintTintLight,

    tertiary = AmberBase,
    onTertiary = AmberInk,
    tertiaryContainer = AmberTintDark,
    onTertiaryContainer = AmberTintLight,

    error = CoralBase,
    onError = CoralInk,
    errorContainer = CoralTintDark,
    onErrorContainer = CoralTintLight,

    background = Canvas,
    onBackground = TextDark,
    surface = Canvas,
    onSurface = TextDark,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextDimDark,
    surfaceTint = VioletBase,
    inverseSurface = TextDark,
    inverseOnSurface = Canvas,

    surfaceContainerLowest = PureBlack,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceMid,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceTop,
    surfaceBright = SurfaceTop,
    surfaceDim = Canvas,

    outline = OutlineDark,
    outlineVariant = LineDark,
    scrim = PureBlack
)

private val BrandLightColors = lightColorScheme(
    primary = VioletDeep,
    onPrimary = PureWhite,
    primaryContainer = VioletTintLight,
    onPrimaryContainer = VioletInk,
    inversePrimary = VioletBase,

    secondary = MintDeep,
    onSecondary = PureWhite,
    secondaryContainer = MintTintLight,
    onSecondaryContainer = MintInk,

    tertiary = AmberDeep,
    onTertiary = PureWhite,
    tertiaryContainer = AmberTintLight,
    onTertiaryContainer = AmberInk,

    error = CoralDeep,
    onError = PureWhite,
    errorContainer = CoralTintLight,
    onErrorContainer = CoralInk,

    background = CanvasLight,
    onBackground = TextLight,
    surface = CanvasLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceHighLight,
    onSurfaceVariant = TextDimLight,
    surfaceTint = VioletDeep,
    inverseSurface = TextLight,
    inverseOnSurface = CanvasLight,

    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = SurfaceLowLight,
    surfaceContainer = SurfaceMidLight,
    surfaceContainerHigh = SurfaceHighLight,
    surfaceContainerHighest = SurfaceTopLight,
    surfaceBright = PureWhite,
    surfaceDim = SurfaceHighLight,

    outline = OutlineLight,
    outlineVariant = LineLight,
    scrim = PureBlack
)

@Composable
fun NizKaryaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You wallpaper colours, an opt-in; brand look is the default. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && supportsDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> BrandDarkColors
        else -> BrandLightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Edge-to-edge: keep system bar icons legible against our surface.
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NizKaryaTypography,
        shapes = NizKaryaShapes,
        content = content
    )
}
