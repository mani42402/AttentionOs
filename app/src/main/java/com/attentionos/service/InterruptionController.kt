package com.attentionos.service

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.attentionos.data.settings.AppSettings
import com.attentionos.domain.AttentionPriority

/**
 * What the app should do about a notification, before anything is actually played.
 *
 * Separated from the playback so the mapping can be tested. Whether a device makes a sound is the
 * platform's business; whether an urgent alert was *supposed* to make one is this app's central
 * promise, and reading the wrong preference would silence it in a way only a user with a missed
 * alert would ever discover.
 */
internal data class InterruptionPlan(
    val sound: Boolean,
    val vibration: Boolean,
) {
    val silent: Boolean get() = !sound && !vibration
}

/** The priority-to-preference mapping, as a pure function. */
internal fun interruptionPlanFor(
    priority: AttentionPriority,
    settings: AppSettings,
): InterruptionPlan = InterruptionPlan(
    sound = when (priority) {
        AttentionPriority.CRITICAL -> settings.criticalSound
        AttentionPriority.HIGH -> settings.highSound
        AttentionPriority.MEDIUM -> settings.mediumSound
        // Nothing below MEDIUM may ever make a sound. This is a floor, not a preference: "can
        // wait" and "quiet" are the two levels whose entire meaning is that they do not.
        AttentionPriority.LOW, AttentionPriority.SILENT -> false
    },
    vibration = when (priority) {
        AttentionPriority.CRITICAL -> settings.criticalVibration
        AttentionPriority.HIGH -> settings.highVibration
        AttentionPriority.MEDIUM -> settings.mediumVibration
        AttentionPriority.LOW, AttentionPriority.SILENT -> false
    },
)

/** Distinct pattern for the top level, so an urgent alert is recognisable without looking. */
internal fun vibrationPatternFor(priority: AttentionPriority): LongArray = when (priority) {
    AttentionPriority.CRITICAL -> longArrayOf(0, 180, 90, 240)
    else -> longArrayOf(0, 160)
}

class InterruptionController(private val context: Context) {
    fun alert(priority: AttentionPriority, settings: AppSettings) {
        val plan = interruptionPlanFor(priority, settings)
        if (plan.sound) playNotificationSound()
        if (plan.vibration) vibrate(priority)
    }

    private fun playNotificationSound() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    audioAttributes = notificationAudioAttributes
                }
                play()
            }
        }
    }

    private fun vibrate(priority: AttentionPriority) {
        val pattern = vibrationPatternFor(priority)
        runCatching {
            vibrator()?.vibrate(
                VibrationEffect.createWaveform(pattern, -1),
                notificationAudioAttributes,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private companion object {
        val notificationAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
