package com.attentionos.service

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.attentionos.data.AppSettings
import com.attentionos.domain.AttentionPriority

class InterruptionController(private val context: Context) {
    fun alert(priority: AttentionPriority, settings: AppSettings) {
        val sound = when (priority) {
            AttentionPriority.CRITICAL -> settings.criticalSound
            AttentionPriority.HIGH -> settings.highSound
            AttentionPriority.MEDIUM -> settings.mediumSound
            AttentionPriority.LOW, AttentionPriority.SILENT -> false
        }
        val vibration = when (priority) {
            AttentionPriority.CRITICAL -> settings.criticalVibration
            AttentionPriority.HIGH -> settings.highVibration
            AttentionPriority.MEDIUM -> settings.mediumVibration
            AttentionPriority.LOW, AttentionPriority.SILENT -> false
        }

        if (sound) playNotificationSound()
        if (vibration) vibrate(priority)
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
        val pattern = when (priority) {
            AttentionPriority.CRITICAL -> longArrayOf(0, 180, 90, 240)
            else -> longArrayOf(0, 160)
        }
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
