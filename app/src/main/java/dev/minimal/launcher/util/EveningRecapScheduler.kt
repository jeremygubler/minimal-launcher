package dev.minimal.launcher.util

import dev.minimal.launcher.util.tr
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
import dev.minimal.launcher.data.EveningRecap
import dev.minimal.launcher.data.FocusSessions
import dev.minimal.launcher.data.IntentionStats
import dev.minimal.launcher.data.RecapInput
import dev.minimal.launcher.data.ScreenTime
import dev.minimal.launcher.data.ScreenTimeMath
import dev.minimal.launcher.launcherApp
import dev.minimal.launcher.pro.Pro
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.concurrent.thread

/** Abendrückblick: tägliche, ruhige Zusammenfassung als Benachrichtigung. */
object EveningRecapScheduler {
    const val EXTRA_SHOW_REPORT = "show_report"
    private const val CHANNEL = "evening_recap"
    private const val NOTIFICATION_ID = 4712
    private const val WINDOW_MS = 10 * 60_000L

    /** Nächsten Rückblick planen oder – wenn ausgeschaltet – abbrechen. */
    fun sync(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(app)
        alarms.cancel(pending)
        val s = app.launcherApp.settings.value
        if (!s.eveningRecap || !Pro.isPro.value) return
        val next = EveningRecap.nextTrigger(ZonedDateTime.now(), s.eveningRecapMinute)
        // Kleines Zeitfenster statt exakter Alarm – braucht keine Sonderberechtigung.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), WINDOW_MS, pending)
    }

    /** Rückblick berechnen und anzeigen (blockiert – nicht auf dem Hauptthread aufrufen). */
    fun show(context: Context) {
        if (!IntentionReminder.canNotify(context)) return
        val app = context.applicationContext
        val s = app.launcherApp.settings.value
        val zone = ZoneId.systemDefault()
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        val access = ScreenTime.hasAccess(app)
        val days = if (access) ScreenTime.lastDays(app, 7) else emptyList()
        val today = days.lastOrNull()?.second.orEmpty()
        val totals = days.map { it.second.values.sum() }
        val earlier = totals.dropLast(1).filter { it > 0 }
        val goalMs = s.dailyGoalMinutes * 60_000L
        val top = today.maxByOrNull { it.value }?.let { (pkg, ms) -> label(app, pkg) to ms }
        val intentions = IntentionStats.summarize(app.launcherApp.intentions.entries(), startOfToday)
        val sessions = FocusSessions.summarize(app.launcherApp.focusSessions.entries(), startOfToday)

        val (title, text) = EveningRecap.compose(
            RecapInput(
                todayMs = if (access) totals.lastOrNull() ?: 0L else null,
                weekAverageMs = if (earlier.isNotEmpty()) earlier.sum() / earlier.size else null,
                goalMs = goalMs,
                streak = ScreenTimeMath.streak(totals, goalMs),
                topApp = top,
                opened = intentions.opened,
                skipped = intentions.skipped,
                tasksDone = s.tasks.count { (it.doneAt ?: 0L) >= startOfToday },
                focusSessions = sessions.completed,
                focusMs = sessions.focusedMs,
            )
        )

        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Leise: erscheint in der Leiste, ohne Ton.
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("Abendrückblick", "Evening recap"), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            app, 1,
            Intent(app, MainActivity::class.java)
                .putExtra(EXTRA_SHOW_REPORT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun label(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, EveningRecapReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class EveningRecapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        thread(name = "evening-recap") {
            try {
                val s = context.launcherApp.settings.value
                if (s.eveningRecap && Pro.isPro.value) EveningRecapScheduler.show(context)
            } catch (_: Exception) {
            } finally {
                // Morgen wieder.
                EveningRecapScheduler.sync(context)
                result.finish()
            }
        }
    }
}
