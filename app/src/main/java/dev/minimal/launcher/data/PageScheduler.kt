package dev.minimal.launcher.data

import dev.minimal.launcher.util.tr
import dev.minimal.launcher.util.isGerman
import java.time.LocalDateTime

/**
 * Zeitplan einer Favoriten-Seite.
 * [days]: 1 = Montag … 7 = Sonntag. [start]/[end]: Minuten seit Mitternacht; [end] < [start] = über Mitternacht.
 */
data class PageSchedule(val days: Set<Int>, val start: Int, val end: Int) {
    fun matches(now: LocalDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        val day = now.dayOfWeek.value
        return if (start <= end) {
            day in days && minute >= start && minute < end
        } else {
            // Über Mitternacht: der Teil nach Mitternacht gehört zum Vortag.
            val yesterday = if (day == 1) 7 else day - 1
            (day in days && minute >= start) || (yesterday in days && minute < end)
        }
    }

    fun describe(): String {
        val names = dayNames()
        val sorted = days.sorted()
        val dayText = when {
            sorted.size == 7 -> tr("Täglich", "Daily")
            sorted.isNotEmpty() && sorted == (sorted.first()..sorted.last()).toList() && sorted.size > 2 ->
                "${names[sorted.first() - 1]}–${names[sorted.last() - 1]}"
            else -> sorted.joinToString(", ") { names[it - 1] }
        }
        return "$dayText ${format(start)}–${format(end)}"
    }

    companion object {
        /** Kurze Wochentagsnamen, Montag zuerst. */
        fun dayNames(): List<String> =
            if (isGerman) listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

        fun format(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
    }
}

object PageScheduler {
    /**
     * Seite, die jetzt aktiv sein soll: zuerst Kontextregeln (genauer), dann Zeitpläne,
     * sonst die erste Seite ohne Regeln.
     */
    fun pageFor(settings: LauncherSettings, now: LocalDateTime, context: ContextState = ContextState()): String =
        settings.pages.firstOrNull { it.context?.matches(context) == true }?.id
            ?: settings.pages.firstOrNull { it.schedule?.matches(now) == true }?.id
            ?: settings.pages.firstOrNull { it.schedule == null && it.context == null }?.id
            ?: settings.pages.first().id
}
