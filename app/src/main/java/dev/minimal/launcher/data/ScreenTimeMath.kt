package dev.minimal.launcher.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Eine Vordergrund-Sitzung einer App. */
data class UsageSession(val pkg: String, val start: Long, val end: Long)

/** Reine Rechenfunktionen für die Bildschirmzeit (ohne Android-Abhängigkeiten, daher testbar). */
object ScreenTimeMath {
    /** Verteilt Sitzungen auf Kalendertage (Sitzungen über Mitternacht werden aufgeteilt). */
    fun perDay(sessions: List<UsageSession>, zone: ZoneId, days: List<LocalDate>): Map<LocalDate, Map<String, Long>> {
        val result = days.associateWith { HashMap<String, Long>() }
        for (s in sessions) {
            if (s.end <= s.start) continue
            var cursor = s.start
            while (cursor < s.end) {
                val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val sliceEnd = minOf(s.end, dayEnd)
                result[day]?.let { it[s.pkg] = (it[s.pkg] ?: 0L) + (sliceEnd - cursor) }
                cursor = sliceEnd
            }
        }
        return result
    }

    /**
     * Anzahl Tage in Folge (von heute rückwärts) mit höchstens [goalMs] Bildschirmzeit.
     * [totals] ist chronologisch (ältester zuerst, heute zuletzt). Heute zählt nur mit,
     * wenn es (noch) unter dem Ziel liegt – ein laufender Tag bricht die Serie nicht.
     */
    fun streak(totals: List<Long>, goalMs: Long): Int {
        if (goalMs <= 0 || totals.isEmpty()) return 0
        var count = if (totals.last() <= goalMs) 1 else 0
        for (i in totals.size - 2 downTo 0) {
            if (totals[i] <= goalMs) count++ else break
        }
        return count
    }
}
