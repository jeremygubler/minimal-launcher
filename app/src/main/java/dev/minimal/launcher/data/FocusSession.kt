package dev.minimal.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Eine Fokus-Sitzung. [completed] = bis zum Ende durchgehalten. */
data class FocusSessionEntry(val start: Long, val end: Long, val completed: Boolean)

/** Wochenauswertung: geschaffte Sitzungen und fokussierte Zeit (nur geschaffte zählen). */
data class FocusSummary(val completed: Int, val focusedMs: Long, val stopped: Int) {
    val isEmpty: Boolean get() = completed == 0 && stopped == 0
}

object FocusSessions {
    val DURATIONS = listOf(25, 50, 90)

    /** Restzeit der laufenden Sitzung (0 = keine). */
    fun remainingMs(settings: LauncherSettings, nowMs: Long): Long =
        if (settings.focusSessionEnd > nowMs) settings.focusSessionEnd - nowMs else 0L

    /** Restliche Minuten, aufgerundet (für „noch 18 min“). */
    fun remainingMinutes(settings: LauncherSettings, nowMs: Long): Int =
        ((remainingMs(settings, nowMs) + 59_999) / 60_000).toInt()

    fun summarize(entries: List<FocusSessionEntry>, from: Long, to: Long = Long.MAX_VALUE): FocusSummary {
        val inRange = entries.filter { it.start in from..to }
        val done = inRange.filter { it.completed }
        return FocusSummary(
            completed = done.size,
            focusedMs = done.sumOf { (it.end - it.start).coerceAtLeast(0) },
            stopped = inRange.size - done.size,
        )
    }
}

/** Lokales Protokoll der Fokus-Sitzungen (nur auf dem Gerät, die letzten 60 Tage). */
class FocusSessionLog(context: Context) {
    private val prefs = context.getSharedPreferences("focus_sessions", Context.MODE_PRIVATE)

    @Synchronized
    fun add(entry: FocusSessionEntry) {
        val cutoff = entry.start - KEEP_MS
        val arr = JSONArray()
        (entries().filter { it.start >= cutoff } + entry).forEach { e ->
            arr.put(JSONObject().apply {
                put("s", e.start)
                put("e", e.end)
                put("c", e.completed)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun entries(): List<FocusSessionEntry> = try {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            FocusSessionEntry(o.optLong("s"), o.optLong("e"), o.optBoolean("c"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private companion object {
        const val KEY = "log"
        const val KEEP_MS = 60L * 24 * 60 * 60 * 1000
    }
}
