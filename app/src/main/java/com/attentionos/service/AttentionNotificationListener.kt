package com.attentionos.service

import android.app.Notification
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.LruCache
import com.attentionos.AttentionApplication
import com.attentionos.data.repository.UserAction
import com.attentionos.domain.NotificationSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AttentionNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val labelCache = LruCache<String, String>(32)
    private val alertedKeys = LruCache<String, Long>(64)
    private val interruptionController by lazy { InterruptionController(this) }
    private var settingsJob: Job? = null

    private val container
        get() = (application as AttentionApplication).container

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            container.warmLanguageModel()
        }
        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            container.currentSettings
                .map { it.focusMode }
                .distinctUntilChanged()
                .collect { enabled ->
                    requestListenerHints(
                        if (enabled) HINT_HOST_DISABLE_NOTIFICATION_EFFECTS else 0,
                    )
                }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        if (sbn.packageName == packageName) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // This callback runs on the main thread. Capture only cheap references here and do the
        // extras unparcel plus the string copies on a worker, so a notification burst cannot
        // stall the UI.
        val key = sbn.key
        val sourcePackage = sbn.packageName
        val postedAt = sbn.postTime
        val isOngoing = sbn.isOngoing

        serviceScope.launch {
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()
                ?.take(MAX_TEXT)
            val text = (
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                )?.toString()?.take(MAX_TEXT)

            val signal = NotificationSignal(
                packageName = sourcePackage,
                title = title,
                text = text,
                postedAt = postedAt,
                isConversation = notification.category == Notification.CATEGORY_MESSAGE ||
                    (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
                        ),
                isOngoing = isOngoing,
                categoryHint = notification.category,
            )

            // Suspend for the stored settings rather than reading the hot flow's current value:
            // during the window before DataStore's first emission that value is still defaults,
            // which would classify early notifications with focus mode and content storage off.
            val settings = container.settingsRepository.settings.first()

            val decision = container.attentionRepository.processPosted(
                key = key,
                appLabel = appLabel(sourcePackage),
                signal = signal,
                settings = settings,
            )
            // Never cancel, replace, snooze, or otherwise modify the source notification.
            // AttentionOS preserves the user's complete notification shade.
            if (settings.focusMode && shouldAlert(key)) {
                interruptionController.alert(decision.priority, settings)
            }
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        val action = when (reason) {
            REASON_CLICK -> UserAction.OPENED
            REASON_CANCEL, REASON_CANCEL_ALL -> UserAction.DISMISSED
            else -> return
        }
        val key = sbn.key
        serviceScope.launch {
            val settings = container.settingsRepository.settings.first()
            container.attentionRepository.recordAction(key, action, settings)
        }
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun shouldAlert(key: String): Boolean {
        // Monotonic: a wall-clock change (timezone, NTP correction) must not defeat dedup.
        val now = SystemClock.elapsedRealtime()
        val previous = alertedKeys[key]
        if (previous != null && now - previous < ALERT_DEDUPLICATION_MILLIS) return false
        alertedKeys.put(key, now)
        return true
    }

    private fun appLabel(packageName: String): String =
        labelCache[packageName] ?: runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse {
            packageName.substringAfterLast('.').replaceFirstChar(Char::titlecase)
        }.also { labelCache.put(packageName, it) }

    private companion object {
        const val MAX_TEXT = 2_000
        const val ALERT_DEDUPLICATION_MILLIS = 30_000L
    }
}
