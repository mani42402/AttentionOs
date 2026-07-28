package com.attentionos.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Touch feedback for the moments that deserve it.
 *
 * The app declared the VIBRATE permission and then never used it from the UI: judging a
 * notification, flipping protection on, finishing a review — all silent. Haptics are most of
 * what separates an interface that feels responsive from one that merely repaints, and they
 * matter more here than in most apps because the core loop is a repeated two-way choice.
 *
 * Tied to the motion preference: someone who turns motion off is asking for a calmer interface,
 * and buzzing at them anyway would ignore that.
 */
class AttentionHaptics(
    private val haptics: HapticFeedback,
    private val enabled: Boolean,
) {
    /** A decision landed — "important" or "can wait". */
    fun confirm() = perform(HapticFeedbackType.LongPress)

    /** A setting toggled, a chip selected. */
    fun select() = perform(HapticFeedbackType.TextHandleMove)

    /** A swipe crossed the threshold where releasing would commit. */
    fun threshold() = perform(HapticFeedbackType.TextHandleMove)

    /** A session finished, onboarding completed. */
    fun celebrate() = perform(HapticFeedbackType.LongPress)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) haptics.performHapticFeedback(type)
    }
}

@Composable
fun rememberHaptics(): AttentionHaptics {
    val haptics = LocalHapticFeedback.current
    val enabled = motionEnabled()
    return remember(haptics, enabled) { AttentionHaptics(haptics, enabled) }
}
