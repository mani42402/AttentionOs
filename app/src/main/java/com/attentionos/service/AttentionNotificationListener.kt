package com.attentionos.service

import android.app.Notification
import android.os.Build
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

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(MAX_TEXT)
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()
            ?.take(MAX_TEXT)
        val signal = NotificationSignal(
            packageName = sbn.packageName,
            title = title,
            text = text,
            postedAt = sbn.postTime,
            isConversation = notification.category == Notification.CATEGORY_MESSAGE ||
                (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
                    ),
            isOngoing = sbn.isOngoing,
            categoryHint = notification.category,
        )
        val key = sbn.key
        val settings = container.currentSettings.value
        val sourcePackage = sbn.packageName

        serviceScope.launch {
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
        val settings = container.currentSettings.value
        serviceScope.launch {
            container.attentionRepository.recordAction(sbn.key, action, settings)
        }
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun shouldAlert(key: String): Boolean {
        val now = System.currentTimeMillis()
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
