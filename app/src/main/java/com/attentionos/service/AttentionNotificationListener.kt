package com.attentionos.service

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.LruCache
import com.attentionos.AttentionApplication
import com.attentionos.data.repository.UserAction
import com.attentionos.domain.NotificationSignal
import com.attentionos.security.SenderIdentity
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
                conversationId = SenderIdentity.of(
                    packageName = sourcePackage,
                    personKey = personKey(extras),
                    shortcutId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        notification.shortcutId
                    } else {
                        null
                    },
                    title = title,
                ),
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
        // REASON_APP_CANCEL is the strongest engagement signal Android gives us and it was being
        // thrown away. When somebody reads or replies inside WhatsApp, WhatsApp removes its own
        // notification — the user never taps ours and never swipes it, so neither CLICK nor
        // CANCEL fires. Every reply the user has ever sent was invisible to the learning.
        //
        // We cannot see the reply text, and do not want to. "The sending app withdrew this while
        // the user was in it" is enough to know they dealt with it.
        val action = when (reason) {
            REASON_CLICK, REASON_APP_CANCEL, REASON_APP_CANCEL_ALL -> UserAction.OPENED

            // A single deliberate swipe is a judgement about that one notification.
            REASON_CANCEL -> UserAction.DISMISSED

            // "Clear all" is not a judgement about anything. It used to be recorded as a
            // dismissal, and a dismissal drives that sender's importance to 0.15 — so every time
            // a user tidied their shade, the app learned *against* whoever was sitting in it.
            // The notifications most likely to be sitting there are the ones already dealt with
            // in the app, which are precisely the people who matter. Bulk clears now teach
            // nothing, which is the honest reading of them.
            REASON_CANCEL_ALL -> return

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

    /**
     * The first [Person] attached to the notification, when the app supplies one.
     *
     * Messaging apps set EXTRA_PEOPLE_LIST for conversations; its key is stable across messages
     * from the same contact, which the notification title is not.
     */
    private fun personKey(extras: Bundle): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val people = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, Person::class.java)
            } else {
                // The type-safe overload only exists from API 33; below that the untyped one is
                // the only option. Guarding solely on API 28 would have thrown NoSuchMethodError
                // on Android 9 through 12 and silently dropped back to title-based identity.
                @Suppress("DEPRECATION")
                extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)
            }
        }.getOrNull() ?: return null
        val person = people.firstOrNull() ?: return null
        return person.key ?: person.uri ?: person.name?.toString()
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
