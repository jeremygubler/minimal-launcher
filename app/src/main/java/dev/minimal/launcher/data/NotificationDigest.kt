package dev.minimal.launcher.data

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import dev.minimal.launcher.util.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

/** Eine zurückgehaltene Benachrichtigung (ohne Intent – der lebt nur im Arbeitsspeicher). */
data class HeldNotification(val key: String, val pkg: String, val title: String, val text: String, val time: Long)

/** Benachrichtigungs-Zusammenfassung: reine Regeln (testbar). */
object NotificationDigest {
    /** Vorgaben für die Zustellzeiten (Minuten des Tages). */
    val PRESETS: List<List<Int>> = listOf(
        listOf(12 * 60, 18 * 60),
        listOf(8 * 60, 12 * 60, 18 * 60),
        listOf(12 * 60, 16 * 60, 20 * 60),
        listOf(18 * 60),
    )

    /** Kategorien, die nie zurückgehalten werden – zeitkritisch. */
    private val NEVER = setOf(
        Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_REMINDER, Notification.CATEGORY_EVENT,
    )

    /** Pakete der ablenkenden Apps (App-Schlüssel „paket/klasse#user“). */
    fun packages(settings: LauncherSettings): Set<String> =
        settings.focusApps.map { it.substringBefore('/') }.toSet()

    fun shouldHold(
        settings: LauncherSettings,
        pro: Boolean,
        pkg: String,
        category: String?,
        ongoing: Boolean,
        clearable: Boolean,
    ): Boolean =
        pro && settings.digestEnabled && !ongoing && clearable && category !in NEVER && pkg in packages(settings)

    fun format(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

    fun describe(times: List<Int>) = times.sorted().joinToString(" · ") { format(it) }

    /** Nächste Zustellung (frühester kommender Zeitpunkt). */
    fun nextTrigger(now: ZonedDateTime, times: List<Int>): ZonedDateTime? =
        times.map { EveningRecap.nextTrigger(now, it) }.minOrNull()

    /** Titel und Zeilen der gesammelten Benachrichtigung. */
    fun summary(held: List<HeldNotification>, label: (String) -> String): Pair<String, List<String>> {
        val title = if (held.size == 1) {
            tr("1 Benachrichtigung", "1 notification")
        } else {
            tr("${held.size} Benachrichtigungen", "${held.size} notifications")
        }
        val lines = held.groupBy { it.pkg }
            .entries.sortedWith(compareByDescending<Map.Entry<String, List<HeldNotification>>> { it.value.size }.thenBy { label(it.key) })
            .map { (pkg, list) ->
                val latest = list.maxByOrNull { it.time }!!
                "${label(pkg)} (${list.size}): " + listOf(latest.title, latest.text).filter { it.isNotBlank() }.joinToString(" – ")
            }
        return title to lines
    }
}

/** Speicher der zurückgehaltenen Benachrichtigungen (übersteht Neustarts; Intents nur im Speicher). */
class DigestStore(context: Context) {
    private val prefs = context.getSharedPreferences("digest", Context.MODE_PRIVATE)
    private val _held = MutableStateFlow(load())
    val held: StateFlow<List<HeldNotification>> = _held.asStateFlow()
    private val intents = HashMap<String, PendingIntent>()

    @Synchronized
    fun add(item: HeldNotification, intent: PendingIntent?) {
        // Gleiche Benachrichtigung (Schlüssel) wird aktualisiert, nicht verdoppelt.
        val next = _held.value.filterNot { it.key == item.key } + item
        intent?.let { intents[item.key] = it }
        save(next.takeLast(MAX))
    }

    fun intent(key: String): PendingIntent? = intents[key]

    @Synchronized
    fun remove(keys: Collection<String>) {
        keys.forEach { intents.remove(it) }
        save(_held.value.filterNot { it.key in keys })
    }

    @Synchronized
    fun clear() {
        intents.clear()
        save(emptyList())
    }

    private fun save(list: List<HeldNotification>) {
        _held.value = list
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(JSONObject().apply {
                put("k", h.key)
                put("p", h.pkg)
                put("ti", h.title)
                put("tx", h.text)
                put("t", h.time)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<HeldNotification> = try {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HeldNotification(o.optString("k"), o.optString("p"), o.optString("ti"), o.optString("tx"), o.optLong("t"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private companion object {
        const val KEY = "held"
        const val MAX = 200
    }
}
