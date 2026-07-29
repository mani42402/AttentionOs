@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.attentionos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.attentionos.R

/**
 * Typography built on Inter, shipped with the app.
 *
 * The previous scale used `FontFamily.SansSerif` — whatever the device happened to provide — and
 * defined only eleven of Material's fifteen roles, so the missing four silently rendered in a
 * different family at different metrics. A product that asks to be trusted with notification
 * content should not look like a system dialog.
 *
 * One variable font file covers every weight, which is why this costs ~860KB rather than the
 * five separate files a static family would need.
 */
private val InterVariable = FontFamily(
    Font(
        R.font.inter_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)

/**
 * Trims the extra leading Android adds above the first line and below the last, so a text block's
 * visual box matches its layout box. Without this, vertical rhythm drifts wherever text sits next
 * to a non-text element.
 */
private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun interStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = InterVariable,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimmedLineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * All fifteen Material roles.
 *
 * Display and headline sizes carry negative tracking, which is what stops large text looking
 * loose; labels carry positive tracking so small uppercase text stays readable.
 */
val AttentionTypography = Typography(
    displayLarge = interStyle(64, 66, FontWeight.ExtraBold, -2.4),
    displayMedium = interStyle(48, 52, FontWeight.ExtraBold, -1.6),
    displaySmall = interStyle(34, 40, FontWeight.Bold, -0.8),

    headlineLarge = interStyle(30, 36, FontWeight.Bold, -0.6),
    headlineMedium = interStyle(25, 31, FontWeight.Bold, -0.4),
    headlineSmall = interStyle(21, 27, FontWeight.SemiBold, -0.2),

    titleLarge = interStyle(19, 25, FontWeight.SemiBold, -0.1),
    titleMedium = interStyle(16, 22, FontWeight.SemiBold),
    titleSmall = interStyle(14, 20, FontWeight.SemiBold),

    bodyLarge = interStyle(16, 24, FontWeight.Normal),
    bodyMedium = interStyle(14, 21, FontWeight.Normal),
    bodySmall = interStyle(12, 17, FontWeight.Normal),

    labelLarge = interStyle(14, 19, FontWeight.SemiBold, 0.1),
    labelMedium = interStyle(12, 16, FontWeight.SemiBold, 0.4),
    labelSmall = interStyle(11, 15, FontWeight.SemiBold, 0.6),
)
