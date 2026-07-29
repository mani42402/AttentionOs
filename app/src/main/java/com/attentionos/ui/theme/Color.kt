package com.attentionos.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Signal Garden.
 *
 * The onboarding artwork and the native interface share the same pigments: ink teal for depth,
 * cream for trust, tangerine for action, mint for calm and yellow for learning. Light mode is a
 * paper rendering of the same system, not a separate visual identity.
 */
object SignalColors {
    val Ink = Color(0xFF06171B)
    val InkRaised = Color(0xFF0D2529)
    val InkHigh = Color(0xFF143238)
    val InkBorder = Color(0xFF29474C)

    val Paper = Color(0xFFF5F0E4)
    val PaperRaised = Color(0xFFFFFCF4)
    val PaperMuted = Color(0xFFE8EFE9)
    val PaperBorder = Color(0xFFD4DCD3)

    val Cream = Color(0xFFF6EEDC)
    val CreamMuted = Color(0xFFAFC1BB)

    val Tangerine = Color(0xFFFF7346)
    val TangerineDark = Color(0xFFC64A24)
    val TangerineContainer = Color(0xFFFFE0D4)

    val Mint = Color(0xFF66E0BE)
    val MintDark = Color(0xFF0E6B59)
    val MintContainer = Color(0xFFD4F3E8)

    val Sun = Color(0xFFF4C95D)
    val SunDark = Color(0xFF765607)
    val SunContainer = Color(0xFFFFEAB2)

    val Critical = Color(0xFFFF654F)
    val CriticalDark = Color(0xFFB83224)
    val Quiet = Color(0xFF78CFAE)
    val Silent = Color(0xFF819398)
}

/** Priority meaning is stable across light and dark; containers provide the required contrast. */
object PriorityColors {
    val critical = SignalColors.Critical
    val high = SignalColors.Tangerine
    val medium = SignalColors.Sun
    val low = SignalColors.Quiet
    val silent = SignalColors.Silent
}

internal val AttentionLightColors = lightColorScheme(
    primary = SignalColors.MintDark,
    onPrimary = Color.White,
    primaryContainer = SignalColors.MintContainer,
    onPrimaryContainer = Color(0xFF08382F),
    inversePrimary = SignalColors.Mint,

    secondary = SignalColors.TangerineDark,
    onSecondary = Color.White,
    secondaryContainer = SignalColors.TangerineContainer,
    onSecondaryContainer = Color(0xFF5C1B0A),

    tertiary = SignalColors.SunDark,
    onTertiary = Color.White,
    tertiaryContainer = SignalColors.SunContainer,
    onTertiaryContainer = Color(0xFF3B2A00),

    error = SignalColors.CriticalDark,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF5A120B),

    background = SignalColors.Paper,
    onBackground = Color(0xFF10262A),
    surface = SignalColors.PaperRaised,
    onSurface = Color(0xFF10262A),
    surfaceVariant = SignalColors.PaperMuted,
    onSurfaceVariant = Color(0xFF526367),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = SignalColors.PaperRaised,
    surfaceContainer = Color(0xFFF0EDE4),
    surfaceContainerHigh = Color(0xFFE9E7DF),
    surfaceContainerHighest = Color(0xFFE0E5DF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6E1D7),
    surfaceTint = SignalColors.MintDark,

    outline = Color(0xFF728184),
    outlineVariant = SignalColors.PaperBorder,
    scrim = Color.Black,
    inverseSurface = SignalColors.Ink,
    inverseOnSurface = SignalColors.Cream,
)

internal val AttentionDarkColors = darkColorScheme(
    primary = SignalColors.Mint,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF124B40),
    onPrimaryContainer = Color(0xFFC9F7E9),
    inversePrimary = SignalColors.MintDark,

    secondary = SignalColors.Tangerine,
    onSecondary = Color(0xFF4C1607),
    secondaryContainer = Color(0xFF5A2717),
    onSecondaryContainer = Color(0xFFFFDCCE),

    tertiary = SignalColors.Sun,
    onTertiary = Color(0xFF3C2C00),
    tertiaryContainer = Color(0xFF55420E),
    onTertiaryContainer = Color(0xFFFFE9A9),

    error = Color(0xFFFF8978),
    onError = Color(0xFF56140B),
    errorContainer = Color(0xFF70251B),
    onErrorContainer = Color(0xFFFFDAD5),

    background = SignalColors.Ink,
    onBackground = SignalColors.Cream,
    surface = SignalColors.InkRaised,
    onSurface = SignalColors.Cream,
    surfaceVariant = SignalColors.InkHigh,
    onSurfaceVariant = SignalColors.CreamMuted,

    surfaceContainerLowest = Color(0xFF031014),
    surfaceContainerLow = Color(0xFF091D21),
    surfaceContainer = SignalColors.InkRaised,
    surfaceContainerHigh = SignalColors.InkHigh,
    surfaceContainerHighest = Color(0xFF1A3C42),
    surfaceBright = Color(0xFF21464C),
    surfaceDim = SignalColors.Ink,
    surfaceTint = SignalColors.Mint,

    outline = Color(0xFF779096),
    outlineVariant = SignalColors.InkBorder,
    scrim = Color.Black,
    inverseSurface = SignalColors.Cream,
    inverseOnSurface = SignalColors.Ink,
)
