package dev.minimal.launcher.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.minimal.launcher.MainActivity
import dev.minimal.launcher.R
import dev.minimal.launcher.data.NotificationDigest
import dev.minimal.launcher.launcherApp
import dev.minimal.launcher.pro.Pro
import java.time.ZonedDateTime

/** Stellt die gesammelten Benachrichtigungen zu den gewählten Zeiten zu. */
object DigestScheduler {
    const val EXTRA_SHOW_DIGEST = "show_digest"
    private const val CHANNEL = "digest"
    private const val NOTIFICATION_ID = 4714
    private const val WINDOW_MS = 10 * 60_000L

    fun sync(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(app)
        alarms.cancel(pending)
        val s = app.launcherApp.settings.value
        if (!s.digestEnabled || !Pro.isPro.value) return
        val next = NotificationDigest.nextTrigger(ZonedDateTime.now(), s.digestTimes) ?: return
        alarms.setWindow(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), WINDOW_MS, pending)
    }

    /** Gesammelte Benachrichtigung zeigen (nichts, wenn nichts zurückgehalten wurde). */
    fun deliver(context: Context) {
        val app = context.applicationContext
        val held = app.launcherApp.digest.held.value
        if (held.isEmpty() || !IntentionReminder.canNotify(app)) return
        val pm = app.packageManager
        val (title, lines) = NotificationDigest.summary(held) { pkg ->
            try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
        }
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("Zusammenfassung", "Digest"), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            app, 3,
            Intent(app, MainActivity::class.java)
                .putExtra(EXTRA_SHOW_DIGEST, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach { style.addLine(it) }
        if (lines.size > 6) style.setSummaryText(tr("+${lines.size - 6} weitere Apps", "+${lines.size - 6} more apps"))
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tr("Zusammenfassung · $title", "Digest · $title"))
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(style)
            .setNumber(held.size)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, DigestReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            DigestScheduler.deliver(context)
        } catch (_: Exception) {
        } finally {
            DigestScheduler.sync(context)
        }
    }
}
