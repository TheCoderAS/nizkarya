package com.nizkarya.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = BrandViolet,
    onPrimary = Color.White,
    secondary = BrandCoral,
    onSecondary = Color.White,
    tertiary = BrandTeal,
    onTertiary = BgDark,
    background = BgDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextMutedDark,
    outline = OutlineDark,
    error = Danger,
    onError = BgDark
)

private val LightColors = lightColorScheme(
    primary = BrandVioletDim,
    onPrimary = Color.White,
    secondary = BrandCoral,
    onSecondary = Color.White,
    tertiary = BrandTeal,
    onTertiary = Color.White,
    background = BgLight,
    onBackground = TextLight,
    surface = SurfaceLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextMutedLight,
    outline = OutlineLight,
    error = Danger,
    onError = Color.White
)

@Composable
fun NizKaryaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
