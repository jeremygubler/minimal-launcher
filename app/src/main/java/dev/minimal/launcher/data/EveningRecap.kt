package dev.minimal.launcher.data

import dev.minimal.launcher.util.tr
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
    val focusSessions: Int = 0,
    val focusMs: Long = 0L,
    val dailyIntention: String? = null,
    val dailyIntentionDone: Boolean = false,
)

/** Text des Abendrückblicks – ruhig, ohne Wertung (rein, daher testbar). */
object EveningRecap {
    private const val MIN = 60_000L

    fun compose(i: RecapInput): Pair<String, String> {
        val title = i.todayMs?.let { tr("Dein Tag · ${ScreenTime.format(it)} Bildschirmzeit", "Your day · ${ScreenTime.format(it)} screen time") } ?: tr("Dein Tag", "Your day")
        val lines = mutableListOf<String>()
        i.dailyIntention?.let { intention ->
            lines += tr("Deine Absicht: $intention", "Your intention: $intention") +
                if (i.dailyIntentionDone) tr(" – erledigt ✓", " – done ✓") else ""
        }
        val today = i.todayMs
        if (today != null && i.goalMs > 0) {
            lines += if (today <= i.goalMs) {
                tr("Im Ziel von ${ScreenTime.format(i.goalMs)}", "Within your goal of ${ScreenTime.format(i.goalMs)}") + if (i.streak > 1) tr(" – ${i.streak} Tage in Folge", " – ${i.streak} days in a row") else ""
            } else {
                tr("${ScreenTime.format(today - i.goalMs)} über deinem Ziel", "${ScreenTime.format(today - i.goalMs)} over your goal")
            }
        }
        val avg = i.weekAverageMs
        if (today != null && avg != null && avg > 0) {
            val diff = today - avg
            if (abs(diff) >= 5 * MIN) {
                lines += ScreenTime.format(abs(diff)) + if (diff < 0) tr(" weniger als dein Schnitt", " less than your average") else tr(" mehr als dein Schnitt", " more than your average")
            }
        }
        i.topApp?.takeIf { it.second >= MIN }?.let { (label, ms) -> lines += tr("Am meisten: $label (${ScreenTime.format(ms)})", "Most used: $label (${ScreenTime.format(ms)})") }
        if (i.opened + i.skipped > 0) {
            lines += tr("${i.opened}× bewusst geöffnet", "${i.opened}× opened mindfully") + if (i.skipped > 0) tr(" · ${i.skipped}× verzichtet", " · ${i.skipped}× skipped") else ""
        }
        if (i.focusSessions > 0) {
            lines += tr(
                (if (i.focusSessions == 1) "1 Fokus-Sitzung" else "${i.focusSessions} Fokus-Sitzungen") + " (${ScreenTime.format(i.focusMs)})",
                (if (i.focusSessions == 1) "1 focus session" else "${i.focusSessions} focus sessions") + " (${ScreenTime.format(i.focusMs)})",
            )
        }
        if (i.tasksDone > 0) lines += if (i.tasksDone == 1) tr("1 Aufgabe erledigt", "1 task done") else tr("${i.tasksDone} Aufgaben erledigt", "${i.tasksDone} tasks done")
        lines += if (lines.isEmpty()) tr("Ein ruhiger Tag. Gute Nacht.", "A quiet day. Good night.") else tr("Gute Nacht.", "Good night.")
        return title to lines.joinToString("\n")
    }

    /** Nächster Auslösezeitpunkt für [minuteOfDay] (heute, falls noch nicht vorbei, sonst morgen). */
    fun nextTrigger(now: java.time.ZonedDateTime, minuteOfDay: Int): java.time.ZonedDateTime {
        val today = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(minuteOfDay.toLong())
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
