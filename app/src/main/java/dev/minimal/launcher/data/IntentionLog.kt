package dev.minimal.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ein Eintrag der Absichtsfrage. [intention] = null: bewusst verzichtet („Lieber nicht“). */
data class IntentionEntry(val time: Long, val pkg: String, val intention: String?)

/** Auswertung für den Wochenbericht. */
data class IntentionSummary(
    val opened: Int,
    val skipped: Int,
    /** Absicht → Anzahl, häufigste zuerst. */
    val byIntention: List<Pair<String, Int>>,
    /** Paket → (geöffnet, verzichtet), meistgeöffnete zuerst. */
    val byApp: List<Triple<String, Int, Int>>,
    val boredom: Int,
    /** Paket, das am häufigsten aus Langeweile geöffnet wurde. */
    val boredomTopApp: String?,
) {
    val isEmpty: Boolean get() = opened == 0 && skipped == 0
}

object IntentionStats {
    const val BOREDOM = "Langeweile"

    fun summarize(entries: List<IntentionEntry>, from: Long, to: Long = Long.MAX_VALUE): IntentionSummary {
        val inRange = entries.filter { it.time in from..to }
        val opened = inRange.filter { it.intention != null }
        // Eigene Texte ohne Rücksicht auf Gross-/Kleinschreibung zusammenfassen, erste Schreibweise gewinnt.
        val spelling = LinkedHashMap<String, String>()
        val counts = HashMap<String, Int>()
        opened.forEach { e ->
            val text = e.intention!!.trim()
            val key = text.lowercase()
            spelling.getOrPut(key) { text }
            counts[key] = (counts[key] ?: 0) + 1
        }
        val byIntention = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { spelling[it.key] })
            .map { spelling.getValue(it.key) to it.value }
        val byApp = inRange.groupBy { it.pkg }
            .map { (pkg, list) -> Triple(pkg, list.count { it.intention != null }, list.count { it.intention == null }) }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third }.thenBy { it.first })
        val bored = opened.filter { it.intention!!.trim().equals(BOREDOM, ignoreCase = true) }
        val boredomTopApp = bored.groupingBy { it.pkg }.eachCount().maxWithOrNull(compareBy({ it.value }, { it.key }))?.key
        return IntentionSummary(
            opened = opened.size,
            skipped = inRange.size - opened.size,
            byIntention = byIntention,
            byApp = byApp,
            boredom = bored.size,
            boredomTopApp = boredomTopApp,
        )
    }
}

/** Lokales Protokoll der Absichtsfrage (nur auf dem Gerät, die letzten 30 Tage). */
class IntentionLog(context: Context) {
    private val prefs = context.getSharedPreferences("intentions", Context.MODE_PRIVATE)

    @Synchronized
    fun add(entry: IntentionEntry) {
        val cutoff = entry.time - KEEP_MS
        val kept = entries().filter { it.time >= cutoff } + entry
        val arr = JSONArray()
        kept.forEach { e ->
            arr.put(JSONObject().apply {
                put("t", e.time)
                put("p", e.pkg)
                e.intention?.let { put("i", it) }
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun entries(): List<IntentionEntry> = try {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            IntentionEntry(o.optLong("t"), o.optString("p"), o.optString("i").ifEmpty { null })
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "log"
        const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    }
}
