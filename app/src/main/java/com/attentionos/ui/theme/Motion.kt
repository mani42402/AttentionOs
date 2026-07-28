package com.attentionos.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset

/** True when the user has left motion effects on. */
val LocalMotionEnabled = staticCompositionLocalOf { true }

/**
 * The app's motion vocabulary.
 *
 * Everything previously animated with a bare `tween`, which is why transitions felt mechanical:
 * a tween moves at a rate nothing physical moves at. Springs carry a sense of weight, and using
 * a small fixed set of them is what makes separate screens feel like one product.
 *
 * Every spec collapses to zero duration when motion is disabled, so honouring the preference is
 * the default rather than something each call site has to remember. Two animations in the old
 * onboarding forgot, which is exactly the failure this design prevents.
 */
object Motion {

    /** Standard easing for non-spring transitions; Material's emphasised-decelerate curve. */
    val emphasised = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val FAST = 180
    const val MEDIUM = 280
    const val SLOW = 420

    /** Small UI reactions: toggles, chips, selection indicators. Quick, barely any overshoot. */
    fun <T> snappy(enabled: Boolean): FiniteAnimationSpec<T> = if (enabled) {
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    } else {
        tween(0)
    }

    /** Content arriving or repositioning. A touch of overshoot reads as responsive, not bouncy. */
    fun <T> gentle(enabled: Boolean): FiniteAnimationSpec<T> = if (enabled) {
        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow)
    } else {
        tween(0)
    }

    /** Celebratory moments only — a visible, deliberate bounce. */
    fun <T> playful(enabled: Boolean): FiniteAnimationSpec<T> = if (enabled) {
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
    } else {
        tween(0)
    }

    /** Gesture-tracking motion: no overshoot, so a card follows the finger exactly. */
    fun <T> tracking(enabled: Boolean): FiniteAnimationSpec<T> = if (enabled) {
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
    } else {
        tween(0)
    }

    fun <T> timed(enabled: Boolean, durationMillis: Int = MEDIUM): AnimationSpec<T> =
        tween(durationMillis = if (enabled) durationMillis else 0, easing = emphasised)

    /** Offset animations need their own overload because IntOffset has no generic vector path. */
    fun offset(enabled: Boolean): FiniteAnimationSpec<IntOffset> = if (enabled) {
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    } else {
        tween(0)
    }
}

/** Reads the motion preference. Prefer this over touching [LocalMotionEnabled] directly. */
@Composable
@ReadOnlyComposable
fun motionEnabled(): Boolean = LocalMotionEnabled.current
