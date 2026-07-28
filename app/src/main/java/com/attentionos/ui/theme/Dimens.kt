package com.attentionos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Spacing on a strict 4dp grid.
 *
 * The old UI used 42 distinct dp values including 7, 9, 11, 13, 23 and 27 — the kind of drift
 * that reads as "slightly off" everywhere without any single screen looking wrong. Anything not
 * on this scale should be a deliberate, commented exception.
 */
object Spacing {
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp
    val giant = 56.dp

    /** Standard screen gutter. */
    val screenHorizontal = 20.dp

    /** Clearance so the last list item is not hidden behind the bottom bar. */
    val bottomBarClearance = 96.dp

    /** Android's minimum comfortable touch target. */
    val minTouchTarget = 48.dp
}

/**
 * Corner radii.
 *
 * The previous code defined a five-step scale and then ignored it, hardcoding thirteen different
 * radii plus two asymmetric ones. Every surface now picks from here.
 */
val AttentionShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Radii for surfaces that are not Material components — hero panels, sheets, pills. */
object Radius {
    val pill = RoundedCornerShape(percent = 50)
    val card = RoundedCornerShape(20.dp)
    val hero = RoundedCornerShape(28.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/**
 * Elevation.
 *
 * Kept low and used sparingly: on a surface-container-based scheme, tone already communicates
 * depth, and stacking shadows on top of it makes an interface look heavier rather than clearer.
 */
object Elevation {
    val flat = 0.dp
    val raised = 1.dp
    val floating = 3.dp
    val overlay = 6.dp
}
