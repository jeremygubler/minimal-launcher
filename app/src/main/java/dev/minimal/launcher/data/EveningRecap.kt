package dev.minimal.launcher.data

import kotlin.math.abs

/** Zahlen für den Abendrückblick. null = ohne Nutzungszugriff unbekannt. */
data class RecapInput(
    val todayMs: Long?,
    val weekAverageMs: Long?,
    val goalMs: Long,
    val streak: Int,
    val topApp: Pair<String, Long>?,
    val opened: Int,
    val skipped: Int,
    val tasksDone: Int,
)

/** Text des Abendrückblicks – ruhig, ohne Wertung (rein, daher testbar). */
object EveningRecap {
    private const val MIN = 60_000L

    fun compose(i: RecapInput): Pair<String, String> {
        val title = i.todayMs?.let { "Dein Tag · ${ScreenTime.format(it)} Bildschirmzeit" } ?: "Dein Tag"
        val lines = mutableListOf<String>()
        val today = i.todayMs
        if (today != null && i.goalMs > 0) {
            lines += if (today <= i.goalMs) {
                "Im Ziel von ${ScreenTime.format(i.goalMs)}" + if (i.streak > 1) " – ${i.streak} Tage in Folge" else ""
            } else {
                "${ScreenTime.format(today - i.goalMs)} über deinem Ziel"
            }
        }
        val avg = i.weekAverageMs
        if (today != null && avg != null && avg > 0) {
            val diff = today - avg
            if (abs(diff) >= 5 * MIN) {
                lines += ScreenTime.format(abs(diff)) + if (diff < 0) " weniger als dein Schnitt" else " mehr als dein Schnitt"
            }
        }
        i.topApp?.takeIf { it.second >= MIN }?.let { (label, ms) -> lines += "Am meisten: $label (${ScreenTime.format(ms)})" }
        if (i.opened + i.skipped > 0) {
            lines += "${i.opened}× bewusst geöffnet" + if (i.skipped > 0) " · ${i.skipped}× verzichtet" else ""
        }
        if (i.tasksDone > 0) lines += if (i.tasksDone == 1) "1 Aufgabe erledigt" else "${i.tasksDone} Aufgaben erledigt"
        lines += if (lines.isEmpty()) "Ein ruhiger Tag. Gute Nacht." else "Gute Nacht."
        return title to lines.joinToString("\n")
    }

    /** Nächster Auslösezeitpunkt für [minuteOfDay] (heute, falls noch nicht vorbei, sonst morgen). */
    fun nextTrigger(now: java.time.ZonedDateTime, minuteOfDay: Int): java.time.ZonedDateTime {
        val today = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(minuteOfDay.toLong())
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
