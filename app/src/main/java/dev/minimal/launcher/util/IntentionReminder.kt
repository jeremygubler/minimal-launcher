package dev.minimal.launcher.util

import dev.minimal.launcher.util.tr
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.minimal.launcher.MainActivity
import dev.minimal.launcher.R

/**
 * Erinnert nach der gewählten Zeit an die Absicht („10 min sind um – du wolltest: …“).
 * Läuft im Launcher-Prozess, der als Startbildschirm praktisch nie beendet wird;
 * kehrt man vorher zum Startbildschirm zurück, entfällt die Erinnerung.
 */
object IntentionReminder {
    private const val CHANNEL = "intention"
    private const val NOTIFICATION_ID = 4711
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context, appLabel: String, intention: String, minutes: Int) {
        cancel()
        if (minutes <= 0 || !canNotify(context)) return
        val app = context.applicationContext
        val task = Runnable {
            pending = null
            show(app, appLabel, intention, minutes)
        }
        pending = task
        handler.postDelayed(task, minutes * 60_000L)
    }

    fun cancel() {
        pending?.let(handler::removeCallbacks)
        pending = null
    }

    private fun show(context: Context, appLabel: String, intention: String, minutes: Int) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("Absichts-Erinnerungen", "Intention reminders"), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val home = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tr("$minutes min $appLabel sind um", "$minutes min of $appLabel are up"))
            .setContentText(tr("Du wolltest: $intention", "You wanted to: $intention"))
            .setContentIntent(home)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
