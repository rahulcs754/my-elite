package com.elite.assistant.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lets Elite read the phone's current notifications ("What did I miss?").
 *
 * Android only connects this service after the user switches Elite on under
 * Settings > Notification access. Elite reads what is in the notification shade right
 * now; nothing is saved to storage.
 */
class EliteNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** One line per useful notification, newest first, e.g. "WhatsApp · Rahul: are you coming? (5 min ago)". */
    private fun digest(max: Int): String {
        val pm = packageManager
        val now = System.currentTimeMillis()
        return activeNotifications.orEmpty()
            .asSequence()
            .filter { it.packageName != packageName && !it.isOngoing && !isGroupSummary(it) }
            .sortedByDescending { it.postTime }
            .take(max)
            .mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                val app = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                }.getOrDefault(sbn.packageName)
                val minutes = (now - sbn.postTime) / 60_000
                "$app · $title: ${text.take(200)} (${minutes} min ago)"
            }
            .joinToString("\n")
    }

    private fun isGroupSummary(sbn: StatusBarNotification) =
        sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

    companion object {
        @Volatile private var instance: EliteNotificationListener? = null

        /** Has the user granted notification access to Elite? */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val me = ComponentName(context, EliteNotificationListener::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Current notifications as text, or null if the service isn't connected yet. */
        fun currentDigest(max: Int = 30): String? = instance?.digest(max)
    }
}
