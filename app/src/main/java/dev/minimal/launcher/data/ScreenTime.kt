package dev.minimal.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/** Bildschirmzeit von heute, berechnet aus den Nutzungsereignissen des Systems (nur lokal). */
object ScreenTime {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Vordergrundzeit pro Paket seit Mitternacht in Millisekunden (ohne den Launcher selbst). */
    fun today(context: Context, now: Long = System.currentTimeMillis()): Map<String, Long> {
        if (!hasAccess(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = try {
            usm.queryEvents(start, now)
        } catch (e: Exception) {
            return emptyMap()
        }
        val event = UsageEvents.Event()
        // Offene Aktivitäten (Paket + Klasse) → Startzeit.
        val open = HashMap<String, Long>()
        val totals = HashMap<String, Long>()
        fun add(pkg: String, ms: Long) {
            if (ms > 0) totals[pkg] = (totals[pkg] ?: 0L) + ms
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val key = pkg + "/" + event.className
            when (event.eventType) {
                RESUMED -> open[key] = event.timeStamp
                PAUSED, STOPPED -> open.remove(key)?.let { add(pkg, event.timeStamp - it) }
            }
        }
        open.forEach { (key, since) -> add(key.substringBefore('/'), now - since) }
        totals.remove(context.packageName)
        return totals
    }

    fun format(ms: Long): String {
        val minutes = ms / 60_000
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 -> "$h h $m min"
            minutes > 0 -> "$m min"
            else -> "< 1 min"
        }
    }

    // UsageEvents.Event.ACTIVITY_RESUMED / ACTIVITY_PAUSED / ACTIVITY_STOPPED
    // (auf älteren Versionen gleichwertig MOVE_TO_FOREGROUND/BACKGROUND).
    private const val RESUMED = 1
    private const val PAUSED = 2
    private const val STOPPED = 23
}
