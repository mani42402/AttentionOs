package com.attentionos.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The AttentionOS colour system.
 *
 * Built as a full Material 3 scheme rather than a handful of brand colours. The previous palette
 * defined fourteen raw values and left every `surfaceContainer*` role unset, so cards, sheets and
 * elevated surfaces silently fell back to Material's default purple-derived baseline — which is
 * much of why the app read as generic regardless of the brand colours layered on top.
 *
 * The identity is a deep indigo primary with a teal secondary: calm, legible at small sizes, and
 * distinct from the blue every system UI already uses.
 */

// ── Brand ramp ────────────────────────────────────────────────────────────────
private val Indigo10 = Color(0xFF0A0B2E)
private val Indigo20 = Color(0xFF191A4A)
private val Indigo30 = Color(0xFF2B2D68)
private val Indigo40 = Color(0xFF4045A8)
private val Indigo80 = Color(0xFFBEC0FF)
private val Indigo90 = Color(0xFFE1E0FF)

// ── Teal (secondary) ──────────────────────────────────────────────────────────
private val Teal10 = Color(0xFF00201A)
private val Teal20 = Color(0xFF00382E)
private val Teal30 = Color(0xFF005143)
private val Teal40 = Color(0xFF006C59)
private val Teal80 = Color(0xFF54DBBC)
private val Teal90 = Color(0xFF74F8D7)

// ── Amber (tertiary) ──────────────────────────────────────────────────────────
private val Amber10 = Color(0xFF2A1700)
private val Amber20 = Color(0xFF452B00)
private val Amber30 = Color(0xFF633F00)
private val Amber40 = Color(0xFF855400)
private val Amber80 = Color(0xFFFFB865)
private val Amber90 = Color(0xFFFFDDB8)

// ── Error ─────────────────────────────────────────────────────────────────────
private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

// ── Neutrals ──────────────────────────────────────────────────────────────────
private val Neutral6 = Color(0xFF0D0E13)
private val Neutral10 = Color(0xFF131318)
private val Neutral12 = Color(0xFF1B1B21)
private val Neutral17 = Color(0xFF212127)
private val Neutral20 = Color(0xFF2A2A31)
private val Neutral22 = Color(0xFF2F2F36)
private val Neutral24 = Color(0xFF35353D)
private val Neutral90 = Color(0xFFE4E1E9)
private val Neutral92 = Color(0xFFEAE7EF)
private val Neutral94 = Color(0xFFF0EDF5)
private val Neutral96 = Color(0xFFF6F2FA)
private val Neutral98 = Color(0xFFFDF8FF)
private val Neutral100 = Color(0xFFFFFFFF)

private val NeutralVariant30 = Color(0xFF46464F)
private val NeutralVariant50 = Color(0xFF777680)
private val NeutralVariant60 = Color(0xFF918F9A)
private val NeutralVariant80 = Color(0xFFC7C5D0)
private val NeutralVariant90 = Color(0xFFE4E1EC)

/**
 * Priority colours.
 *
 * Deliberately identical across light and dark. A user learns "amber means it can wait" once;
 * shifting the hue between themes would make them relearn it. Contrast is carried by the
 * container each is drawn on rather than by changing the colour itself.
 */
object PriorityColors {
    val critical = Color(0xFFE5484D)
    val high = Color(0xFFF76B15)
    val medium = Color(0xFF5257C7)
    val low = Color(0xFF3E9B8F)
    val silent = Color(0xFF8B8A94)
}

internal val AttentionLightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Neutral100,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    inversePrimary = Indigo80,

    secondary = Teal40,
    onSecondary = Neutral100,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,

    tertiary = Amber40,
    onTertiary = Neutral100,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,

    error = Red40,
    onError = Neutral100,
    errorContainer = Red90,
    onErrorContainer = Red10,

    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,

    // The elevation family the previous scheme omitted entirely.
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral92,
    surfaceContainerHighest = Neutral90,
    surfaceBright = Neutral98,
    surfaceDim = Neutral90,
    surfaceTint = Indigo40,

    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    scrim = Color(0xFF000000),
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral96,
)

internal val AttentionDarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    inversePrimary = Indigo40,

    secondary = Teal80,
    onSecondary = Teal20,
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal90,

    tertiary = Amber80,
    onTertiary = Amber20,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,

    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,

    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,

    surfaceContainerLowest = Color(0xFF08090D),
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22,
    surfaceBright = Neutral24,
    surfaceDim = Neutral6,
    surfaceTint = Indigo80,

    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    scrim = Color(0xFF000000),
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
)
