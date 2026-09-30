package dev.minimal.launcher.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Eine Aufgabe der eingebauten Liste. [doneAt] = Zeitpunkt des Abhakens. */
data class TaskItem(
    val id: String,
    val title: String,
    val due: LocalDate? = null,
    val doneAt: Long? = null,
) {
    val isDone: Boolean get() = doneAt != null
}

/** Reine Logik der Aufgabenliste (testbar ohne Android). */
object Tasks {
    private val prefix = Regex("^(todo|aufgabe|task)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val dueWords = listOf(
        "übermorgen" to 2L, "uebermorgen" to 2L, "day after tomorrow" to 2L,
        "morgen" to 1L, "tomorrow" to 1L, "heute" to 0L, "today" to 0L,
    )

    /** Erkennt „todo …“ in der Suche. */
    fun parseCommand(input: String, today: LocalDate): Pair<String, LocalDate?>? {
        val m = prefix.matchEntire(input.trim()) ?: return null
        return parseText(m.groupValues[2], today)
    }

    /** Liest optional „heute/morgen/übermorgen“ am Anfang oder Ende heraus. */
    fun parseText(text: String, today: LocalDate): Pair<String, LocalDate?>? {
        var title = text.trim()
        var due: LocalDate? = null
        for ((word, days) in dueWords) {
            val lower = title.lowercase()
            when {
                lower == word -> {
                    title = ""
                    due = today.plusDays(days)
                }
                lower.startsWith("$word ") -> {
                    title = title.substring(word.length).trim()
                    due = today.plusDays(days)
                }
                lower.endsWith(" $word") -> {
                    title = title.substring(0, title.length - word.length).trim()
                    due = today.plusDays(days)
                }
            }
            if (due != null) break
        }
        return if (title.isEmpty()) null else title to due
    }

    /** Aufgaben für den Startbildschirm: fällig bis heute; erledigte nur am selben Tag. Offene zuerst. */
    fun visible(tasks: List<TaskItem>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<TaskItem> =
        tasks.filter { t ->
            (t.due == null || !t.due.isAfter(today)) &&
                (t.doneAt == null || Instant.ofEpochMilli(t.doneAt).atZone(zone).toLocalDate() == today)
        }.sortedWith(compareBy<TaskItem>({ it.isDone }, { it.due ?: LocalDate.MAX }))

    /** Entfernt Aufgaben, die vor heute erledigt wurden. */
    fun purge(tasks: List<TaskItem>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<TaskItem> =
        tasks.filter { t -> t.doneAt == null || !Instant.ofEpochMilli(t.doneAt).atZone(zone).toLocalDate().isBefore(today) }
}
