package dev.minimal.launcher.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import dev.minimal.launcher.MainActivity
import dev.minimal.launcher.R
import dev.minimal.launcher.data.FocusSessionEntry
import dev.minimal.launcher.launcherApp

/**
 * Fokus-Sitzung: Start/Ende im Einstellungsspeicher (übersteht Neustarts), Abschluss per Handler
 * im Launcher-Prozess. Wurde der Prozess beendet, schliesst [sync] die Sitzung beim nächsten Start ab.
 */
object FocusSessionTimer {
    private const val CHANNEL = "focus_session"
    private const val NOTIFICATION_ID = 4713
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun start(context: Context, minutes: Int) {
        val now = System.currentTimeMillis()
        context.launcherApp.settings.update { it.copy(focusSessionStart = now, focusSessionEnd = now + minutes * 60_000L) }
        sync(context)
    }

    /** Vorzeitig beenden – zählt als abgebrochen. */
    fun stop(context: Context) {
        val app = context.applicationContext
        val s = app.launcherApp.settings.value
        if (s.focusSessionEnd == 0L) return
        cancel()
        app.launcherApp.focusSessions.add(FocusSessionEntry(s.focusSessionStart, System.currentTimeMillis(), completed = false))
        clear(app)
    }

    /** Laufende Sitzung planen bzw. eine abgelaufene abschliessen. */
    fun sync(context: Context) {
        val app = context.applicationContext
        cancel()
        val s = app.launcherApp.settings.value
        if (s.focusSessionEnd == 0L) return
        val left = s.focusSessionEnd - System.currentTimeMillis()
        if (left <= 0) {
            finish(app)
        } else {
            val task = Runnable {
                pending = null
                finish(app)
            }
            pending = task
            handler.postDelayed(task, left)
        }
    }

    private fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }

    private fun clear(context: Context) =
        context.launcherApp.settings.update { it.copy(focusSessionStart = 0L, focusSessionEnd = 0L) }

    private fun finish(context: Context) {
        val s = context.launcherApp.settings.value
        if (s.focusSessionEnd == 0L) return
        context.launcherApp.focusSessions.add(FocusSessionEntry(s.focusSessionStart, s.focusSessionEnd, completed = true))
        clear(context)
        val minutes = ((s.focusSessionEnd - s.focusSessionStart) / 60_000).toInt()
        notifyDone(context, minutes)
    }

    private fun notifyDone(context: Context, minutes: Int) {
        if (!IntentionReminder.canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("Fokus-Sitzungen", "Focus sessions"), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val home = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tr("Fokus-Sitzung geschafft · $minutes min", "Focus session done · $minutes min"))
            .setContentText(tr("Gut gemacht. Zeit für eine kurze Pause.", "Well done. Time for a short break."))
            .setContentIntent(home)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
