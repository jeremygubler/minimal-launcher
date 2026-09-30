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

    /** Vordergrund-Sitzungen im Zeitraum (ohne den Launcher selbst). */
    fun sessions(context: Context, from: Long, to: Long): List<UsageSession> {
        if (!hasAccess(context)) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = try {
            usm.queryEvents(from, to)
        } catch (e: Exception) {
            return emptyList()
        }
        val event = UsageEvents.Event()
        // Offene Aktivitäten (Paket + Klasse) → Startzeit.
        val open = HashMap<String, Long>()
        val result = ArrayList<UsageSession>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg == context.packageName) continue
            val key = pkg + "/" + event.className
            when (event.eventType) {
                RESUMED -> open[key] = event.timeStamp
                PAUSED, STOPPED -> open.remove(key)?.let { result += UsageSession(pkg, it, event.timeStamp) }
            }
        }
        open.forEach { (key, since) -> result += UsageSession(key.substringBefore('/'), since, to) }
        return result
    }

    /** Vordergrundzeit pro Paket seit Mitternacht in Millisekunden. */
    fun today(context: Context, now: Long = System.currentTimeMillis()): Map<String, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        return ScreenTimeMath.perDay(sessions(context, start, now), zone, listOf(today))[today].orEmpty()
    }

    /** Heute: wie oft [pkg] geöffnet wurde und wie lange – null ohne Nutzungszugriff. */
    fun appToday(context: Context, pkg: String, now: Long = System.currentTimeMillis()): Pair<Int, Long>? {
        if (!hasAccess(context)) return null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = sessions(context, start, now)
        val ms = ScreenTimeMath.perDay(sessions, zone, listOf(today))[today]?.get(pkg) ?: 0L
        return ScreenTimeMath.opens(sessions, pkg) to ms
    }

    /** Nutzung pro Tag für die letzten [days] Tage (ältester zuerst, heute zuletzt). */
    fun lastDays(context: Context, days: Int = 7, now: Long = System.currentTimeMillis()): List<Pair<LocalDate, Map<String, Long>>> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val start = dates.first().atStartOfDay(zone).toInstant().toEpochMilli()
        val perDay = ScreenTimeMath.perDay(sessions(context, start, now), zone, dates)
        return dates.map { it to perDay[it].orEmpty() }
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
