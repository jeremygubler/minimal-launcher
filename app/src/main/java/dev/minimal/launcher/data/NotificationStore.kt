package dev.minimal.launcher.data

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import dev.minimal.launcher.service.NotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationPreview(
    val key: String,
    val appKey: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val intent: PendingIntent?,
    val autoCancel: Boolean,
    val clearable: Boolean,
)

object NotificationStore {
    private val _items = MutableStateFlow<List<NotificationPreview>>(emptyList())
    val items: StateFlow<List<NotificationPreview>> = _items.asStateFlow()

    @Volatile
    internal var service: NotificationListener? = null

    internal fun publish(list: List<NotificationPreview>) {
        _items.value = list
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun dismiss(item: NotificationPreview) {
        if (!item.clearable) return
        try {
            service?.cancelNotification(item.key)
        } catch (_: Exception) {
        }
    }

    fun open(context: Context, item: NotificationPreview) {
        val pi = item.intent ?: return
        try {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            pi.send(context, 0, null, null, null, null, options)
            if (item.autoCancel) dismiss(item)
        } catch (_: Exception) {
        }
    }
}
