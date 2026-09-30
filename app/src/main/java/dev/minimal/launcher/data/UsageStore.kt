package dev.minimal.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.exp

/**
 * Merkt sich lokal, wie oft und wann Apps gestartet wurden – für Vorschläge und
 * die Sortierung der Suchergebnisse. Nichts davon verlässt das Gerät.
 */
class UsageStore(context: Context) {
    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    private data class Entry(val score: Double, val last: Long)

    private val _scores = MutableStateFlow(load())
    /** App-Schlüssel → Nutzungswert (ältere Starts zählen weniger). */
    val scores: StateFlow<Map<String, Double>> = _scores.asStateFlow()

    fun record(key: String, now: Long = System.currentTimeMillis()) {
        val old = read(key)
        val decayed = old?.let { it.score * decay(now - it.last) } ?: 0.0
        val entry = Entry(decayed + 1.0, now)
        prefs.edit().putString(key, "${entry.score}|${entry.last}").apply()
        _scores.value = _scores.value + (key to entry.score)
    }

    /** App-Schlüssel → Zeitpunkt des letzten Starts über den Launcher. */
    fun lastLaunches(): Map<String, Long> = prefs.all.mapNotNull { (key, value) ->
        (value as? String)?.let(::parse)?.let { key to it.last }
    }.toMap()

    fun clear() {
        prefs.edit().clear().apply()
        _scores.value = emptyMap()
    }

    private fun read(key: String): Entry? = prefs.getString(key, null)?.let(::parse)

    private fun load(): Map<String, Double> {
        val now = System.currentTimeMillis()
        return prefs.all.mapNotNull { (key, value) ->
            (value as? String)?.let(::parse)?.let { key to it.score * decay(now - it.last) }
        }.toMap()
    }

    private fun parse(raw: String): Entry? {
        val parts = raw.split('|')
        return Entry(parts.getOrNull(0)?.toDoubleOrNull() ?: return null, parts.getOrNull(1)?.toLongOrNull() ?: return null)
    }

    companion object {
        private const val HALF_LIFE_MS = 7L * 24 * 60 * 60 * 1000

        /** Halbwertszeit von einer Woche. */
        fun decay(ageMs: Long): Double = exp(-ageMs.coerceAtLeast(0) * Math.log(2.0) / HALF_LIFE_MS)
    }
}
