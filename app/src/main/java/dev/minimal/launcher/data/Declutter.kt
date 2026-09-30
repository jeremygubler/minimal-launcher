package dev.minimal.launcher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo

/** Eine App, die lange nicht geöffnet wurde. [lastUsed] = null: im ganzen Zeitraum nie. */
data class UnusedApp(val app: AppInfo, val lastUsed: Long?, val removable: Boolean)

/** „Aufräumen“: findet Apps, die seit einer Weile nicht mehr benutzt wurden. */
object Declutter {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** Letzte Nutzung pro Paket laut System (bis zu einem Jahr zurück). Leer ohne Nutzungszugriff. */
    fun systemLastUsed(context: Context, now: Long = System.currentTimeMillis()): Map<String, Long> {
        if (!ScreenTime.hasAccess(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val stats = try {
            usm.queryAndAggregateUsageStats(now - 365 * DAY_MS, now)
        } catch (e: Exception) {
            return emptyMap()
        }
        return stats.mapValues { it.value.lastTimeUsed }.filterValues { it > 0 }
    }

    fun isRemovable(app: AppInfo): Boolean {
        // System-Apps (auch aktualisierte) lassen sich nicht entfernen – nur ausblenden.
        return app.info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }

    /**
     * Wählt Apps aus, die seit mindestens [days] Tagen nicht geöffnet wurden.
     * Frisch installierte Apps (jünger als der Zeitraum) werden nie vorgeschlagen.
     * Sortierung: nie benutzte zuerst, dann die am längsten unbenutzten.
     */
    fun <T> select(
        items: List<T>,
        packageName: (T) -> String,
        installTime: (T) -> Long,
        launcherLast: (T) -> Long?,
        systemLast: Map<String, Long>,
        now: Long,
        days: Int,
    ): List<Pair<T, Long?>> {
        val threshold = now - days * DAY_MS
        return items.mapNotNull { item ->
            if (installTime(item) > threshold) return@mapNotNull null
            val last = listOfNotNull(systemLast[packageName(item)], launcherLast(item)).filter { it > 0 }.maxOrNull()
            if (last != null && last >= threshold) null else item to last
        }.sortedBy { it.second ?: Long.MIN_VALUE }
    }
}
