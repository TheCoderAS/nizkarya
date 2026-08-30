package com.nizkarya.app.ui.theme

import androidx.compose.ui.graphics.Color

// NizKarya's palette. Dark is the design target: a near-black canvas with
// four accents that carry meaning (violet tasks, mint habits, amber streaks,
// coral late). Light is a real mirror of it, not an inversion, so the accents
// darken enough to stay legible on white.
//
// The accent hues themselves live in Accents.kt, since components read them
// through LocalAccent rather than through the Material scheme.

// Violet, the primary. Dark theme uses the bright cut, light theme the deep one.
val VioletBase = Color(0xFF7C6CFF)
val VioletDeep = Color(0xFF5A45E0)
val VioletInk = Color(0xFF1B1148)
val VioletTintDark = Color(0xFF2A2350)
val VioletTintLight = Color(0xFFE7E2FF)

// Mint, used for habits and for "done" everywhere.
val MintBase = Color(0xFF2ED3B7)
val MintDeep = Color(0xFF0E8A75)
val MintInk = Color(0xFF00201A)
val MintTintDark = Color(0xFF10382F)
val MintTintLight = Color(0xFFD3F5EC)

// Amber, used for streaks and progress.
val AmberBase = Color(0xFFFFB020)
val AmberDeep = Color(0xFFA96A00)
val AmberInk = Color(0xFF2A1800)
val AmberTintDark = Color(0xFF3A2A08)
val AmberTintLight = Color(0xFFFFEBC7)

// Coral, used for overdue, the now line, and destructive actions.
val CoralBase = Color(0xFFFF5F6D)
val CoralDeep = Color(0xFFCE2C3E)
val CoralInk = Color(0xFF2E0006)
val CoralTintDark = Color(0xFF3D131A)
val CoralTintLight = Color(0xFFFFE0E2)

// The dark canvas. Not pure black: cards need somewhere to sit without a
// border doing all the work.
val Canvas = Color(0xFF0A0A0F)
val SurfaceLow = Color(0xFF14141C)
val SurfaceMid = Color(0xFF191922)
val SurfaceHigh = Color(0xFF1C1C26)
val SurfaceTop = Color(0xFF23232F)
val LineDark = Color(0xFF26262F)
val OutlineDark = Color(0xFF4C4C60)
val TextDark = Color(0xFFF3F3F8)
val TextDimDark = Color(0xFF9A9AAE)

// The light mirror. A hair of violet in the greys, so the neutral reads as
// chosen rather than inherited.
val CanvasLight = Color(0xFFF7F6FB)
val SurfaceLowLight = Color(0xFFFFFFFF)
val SurfaceMidLight = Color(0xFFF3F2F9)
val SurfaceHighLight = Color(0xFFEDECF5)
val SurfaceTopLight = Color(0xFFE5E4F0)
val LineLight = Color(0xFFE2E1EC)
val OutlineLight = Color(0xFF77758A)
val TextLight = Color(0xFF15141C)
val TextDimLight = Color(0xFF5D5B6C)

val PureWhite = Color(0xFFFFFFFF)
val PureBlack = Color(0xFF000000)

// Kept for the semantic helpers in Common.kt, which ask for "success" and
// "warning" rather than for a named accent.
val SuccessDark = MintBase
val SuccessLight = MintDeep
val WarningDark = AmberBase
val WarningLight = AmberDeep
