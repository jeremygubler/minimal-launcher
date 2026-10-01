package dev.minimal.launcher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.HeldNotification
import dev.minimal.launcher.data.NotificationDigest
import dev.minimal.launcher.launcherApp
import dev.minimal.launcher.pro.Pro
import dev.minimal.launcher.data.NotificationPreview
import dev.minimal.launcher.data.NotificationStore
import dev.minimal.launcher.data.NowPlaying

class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationStore.service = this
        publish()
        NowPlaying.start(this)
    }

    override fun onListenerDisconnected() {
        NotificationStore.service = null
        NotificationStore.publish(emptyList())
        NowPlaying.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null && hold(sbn)) return
        publish()
    }

    /** Zusammenfassung: Benachrichtigung einer ablenkenden App zurückhalten (aus der Leiste nehmen). */
    private fun hold(sbn: StatusBarNotification): Boolean {
        val app = applicationContext.launcherApp
        val n = sbn.notification
        if (!NotificationDigest.shouldHold(app.settings.value, Pro.isPro.value, sbn.packageName, n.category, sbn.isOngoing, sbn.isClearable)) {
            return false
        }
        val isSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (!isSummary) {
            val extras = n.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
            if (title.isNotBlank() || text.isNotBlank()) {
                app.digest.add(HeldNotification(sbn.key, sbn.packageName, title, text, sbn.postTime), n.contentIntent)
            }
        }
        try {
            cancelNotification(sbn.key)
        } catch (_: Exception) {
            return false
        }
        return true
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        val active = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return

        val relevant = active.filter { !it.isOngoing }
        // Gruppen-Zusammenfassungen nur anzeigen, wenn es keine Einzel-Benachrichtigungen der Gruppe gibt.
        val groupsWithChildren = relevant
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .mapNotNull { it.groupKey }
            .toSet()

        val list = relevant
            .filter { sbn ->
                val isSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                !isSummary || sbn.groupKey !in groupsWithChildren
            }
            .map { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
                NotificationPreview(
                    key = sbn.key,
                    appKey = AppInfo.notificationKey(sbn.packageName, sbn.user),
                    title = title,
                    text = text,
                    postTime = sbn.postTime,
                    intent = sbn.notification.contentIntent,
                    autoCancel = sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
                    clearable = sbn.isClearable,
                )
            }
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .distinctBy { Triple(it.appKey, it.title, it.text) }
            .sortedByDescending { it.postTime }

        NotificationStore.publish(list)
    }
}
