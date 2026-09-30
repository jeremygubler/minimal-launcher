package dev.minimal.launcher.util

import dev.minimal.launcher.util.tr
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

/** Schnellaktionen, die aus dem Suchtext erkannt werden – komplett offline. */
sealed interface QuickAction {
    val title: String
    val icon: String

    data class OpenUrl(val url: String) : QuickAction {
        override val title get() = tr("Öffnen: ", "Open: ") + url.removePrefix("https://").removePrefix("http://")
        override val icon get() = "🌐"
    }

    data class Call(val number: String) : QuickAction {
        override val title get() = tr("Anrufen: $number", "Call: $number")
        override val icon get() = "📞"
    }

    data class Sms(val number: String) : QuickAction {
        override val title get() = tr("SMS an $number", "Text $number")
        override val icon get() = "💬"
    }

    data class SystemSetting(val label: String, val action: String) : QuickAction {
        override val title get() = label
        override val icon get() = "⚙"
    }

    data class Timer(val seconds: Int) : QuickAction {
        override val title get() = tr("Timer starten: ", "Start timer: ") + describeDuration(seconds)
        override val icon get() = "⏱"
    }

    data class Alarm(val hour: Int, val minute: Int) : QuickAction {
        override val title get() = tr("Wecker stellen: %02d:%02d", "Set alarm: %02d:%02d").format(hour, minute)
        override val icon get() = "⏰"
    }

    /** Nur Anzeige (antippen kopiert das Ergebnis). */
    data class Conversion(val text: String, val value: String) : QuickAction {
        override val title get() = text
        override val icon get() = "⇄"
    }
}

private fun describeDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it h" },
        m.takeIf { it > 0 }?.let { "$it min" },
        s.takeIf { it > 0 }?.let { "$it s" },
    ).joinToString(" ").ifEmpty { "0 s" }
}

object QuickActions {
    fun parse(input: String): List<QuickAction> {
        val q = input.trim()
        if (q.isEmpty()) return emptyList()
        return buildList {
            timer(q)?.let(::add)
            alarm(q)?.let(::add)
            addAll(conversions(q))
            url(q)?.let(::add)
            phone(q)?.let { addAll(listOf(QuickAction.Call(it), QuickAction.Sms(it))) }
            addAll(settings(q))
        }
    }

    // --- Timer & Wecker -------------------------------------------------------

    private val timerRegex = Regex(
        """^(?:timer|countdown)\s+(\d+)\s*(s|sek|sekunden|sec|secs|seconds?|m|min|mins|minuten|minutes?|h|std|stunden|hrs?|hours?)?$|^(\d+)\s*(s|sek|sekunden|sec|secs|seconds?|m|min|mins|minuten|minutes?|h|std|stunden|hrs?|hours?)\s+timer$""",
        RegexOption.IGNORE_CASE,
    )

    private fun timer(q: String): QuickAction.Timer? {
        val m = timerRegex.matchEntire(q) ?: return null
        val amount = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toIntOrNull() ?: return null
        val unit = m.groupValues[2].ifEmpty { m.groupValues[4] }.lowercase()
        val seconds = when (unit) {
            "s", "sek", "sekunden", "sec", "secs", "second", "seconds" -> amount
            "h", "std", "stunden", "hr", "hrs", "hour", "hours" -> amount * 3600
            else -> amount * 60
        }
        return if (seconds in 1..86_400) QuickAction.Timer(seconds) else null
    }

    private val alarmRegex = Regex("""^(?:wecker|alarm)\s+(\d{1,2})(?:[:.](\d{2}))?(?:\s*uhr)?$""", RegexOption.IGNORE_CASE)

    private fun alarm(q: String): QuickAction.Alarm? {
        val m = alarmRegex.matchEntire(q) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        return if (h in 0..23 && min in 0..59) QuickAction.Alarm(h, min) else null
    }

    // --- Webadresse & Telefon ---------------------------------------------------

    private val urlRegex = Regex("""^(https?://)?([a-z0-9-]+\.)+[a-z]{2,}(:\d+)?(/\S*)?$""", RegexOption.IGNORE_CASE)

    private fun url(q: String): QuickAction.OpenUrl? {
        if (!urlRegex.matches(q)) return null
        // Zahlen wie "3.14" sind keine Adresse.
        if (q.substringBefore('/').all { it.isDigit() || it == '.' }) return null
        return QuickAction.OpenUrl(if (q.startsWith("http", ignoreCase = true)) q else "https://$q")
    }

    private val phoneRegex = Regex("""^\+?[0-9][0-9 ()/\-]{5,}$""")

    private fun phone(q: String): String? {
        if (!phoneRegex.matches(q)) return null
        val digits = q.count { it.isDigit() }
        return if (digits in 6..15) q.filter { it.isDigit() || it == '+' } else null
    }

    // --- Systemeinstellungen ----------------------------------------------------

    private data class SettingEntry(val label: String, val action: String, val keywords: List<String>)

    private val settingEntries: List<SettingEntry> get() = listOf(
        SettingEntry(tr("WLAN-Einstellungen", "Wi-Fi settings"), Settings.ACTION_WIFI_SETTINGS, listOf("wlan", "wifi", "wi-fi")),
        SettingEntry(tr("Bluetooth-Einstellungen", "Bluetooth settings"), Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "bt")),
        SettingEntry(tr("Netzwerk & Internet", "Network & internet"), Settings.ACTION_WIRELESS_SETTINGS, listOf("netzwerk", "internet", "mobile daten", "daten", "network", "mobile data")),
        SettingEntry(tr("Flugmodus", "Airplane mode"), Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("flugmodus", "flugzeug", "airplane", "flight mode")),
        SettingEntry(tr("Akku", "Battery"), Intent.ACTION_POWER_USAGE_SUMMARY, listOf("akku", "batterie", "energie", "battery")),
        SettingEntry("Display", Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "bildschirm", "helligkeit", "dunkelmodus", "screen", "brightness", "dark mode")),
        SettingEntry(tr("Töne & Vibration", "Sound & vibration"), Settings.ACTION_SOUND_SETTINGS, listOf("ton", "töne", "lautstärke", "klingelton", "vibration", "sound", "volume", "ringtone")),
        SettingEntry(tr("Standort", "Location"), Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("standort", "gps", "ortung", "location")),
        SettingEntry("Apps", Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "anwendungen", "applications")),
        SettingEntry(tr("Speicher", "Storage"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("speicher", "speicherplatz", "storage")),
        SettingEntry("NFC", Settings.ACTION_NFC_SETTINGS, listOf("nfc", "kontaktlos", "contactless")),
        SettingEntry(tr("Datum & Uhrzeit", "Date & time"), Settings.ACTION_DATE_SETTINGS, listOf("datum", "uhrzeit", "zeitzone", "date", "time", "time zone")),
        SettingEntry(tr("Sprache", "Language"), Settings.ACTION_LOCALE_SETTINGS, listOf("sprache", "language")),
        SettingEntry(tr("Sicherheit & Datenschutz", "Security & privacy"), Settings.ACTION_SECURITY_SETTINGS, listOf("sicherheit", "datenschutz", "fingerabdruck", "security", "privacy", "fingerprint")),
        SettingEntry(tr("Bedienungshilfen", "Accessibility"), Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("bedienungshilfe", "barrierefreiheit", "accessibility")),
        SettingEntry(tr("Entwickleroptionen", "Developer options"), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("entwickler", "developer")),
        SettingEntry(tr("Einstellungen", "Settings"), Settings.ACTION_SETTINGS, listOf("einstellungen", "settings")),
    )

    private fun settings(q: String): List<QuickAction.SystemSetting> {
        val query = q.lowercase(Locale.GERMAN)
        if (query.length < 3) return emptyList()
        return settingEntries.filter { e -> e.keywords.any { it.startsWith(query) || query == it } }
            .take(3)
            .map { QuickAction.SystemSetting(it.label, it.action) }
    }

    // --- Einheiten --------------------------------------------------------------

    private enum class Dim { LENGTH, MASS, VOLUME, SPEED, TEMP }

    private data class UnitDef(val name: String, val dim: Dim, val factor: Double)

    private val units: Map<String, UnitDef> = buildMap {
        fun add(names: List<String>, dim: Dim, factor: Double) = names.forEach { put(it, UnitDef(names.first(), dim, factor)) }
        add(listOf("km", "kilometer"), Dim.LENGTH, 1000.0)
        add(listOf("m", "meter"), Dim.LENGTH, 1.0)
        add(listOf("cm", "zentimeter"), Dim.LENGTH, 0.01)
        add(listOf("mm", "millimeter"), Dim.LENGTH, 0.001)
        add(listOf("mi", "meile", "meilen", "mile", "miles"), Dim.LENGTH, 1609.344)
        add(listOf("ft", "fuss", "fuß", "feet", "foot"), Dim.LENGTH, 0.3048)
        add(listOf("in", "zoll", "inch", "inches"), Dim.LENGTH, 0.0254)
        add(listOf("yd", "yard", "yards"), Dim.LENGTH, 0.9144)
        add(listOf("kg", "kilo", "kilogramm"), Dim.MASS, 1.0)
        add(listOf("g", "gramm"), Dim.MASS, 0.001)
        add(listOf("t", "tonne", "tonnen"), Dim.MASS, 1000.0)
        add(listOf("lb", "lbs", "pfund", "pound", "pounds"), Dim.MASS, 0.45359237)
        add(listOf("oz", "unze", "unzen", "ounce", "ounces"), Dim.MASS, 0.028349523125)
        add(listOf("l", "liter"), Dim.VOLUME, 1.0)
        add(listOf("ml", "milliliter"), Dim.VOLUME, 0.001)
        add(listOf("gal", "gallon", "gallonen"), Dim.VOLUME, 3.785411784)
        add(listOf("km/h", "kmh", "kph"), Dim.SPEED, 1 / 3.6)
        add(listOf("mph"), Dim.SPEED, 0.44704)
        add(listOf("m/s", "ms"), Dim.SPEED, 1.0)
        add(listOf("kn", "knoten", "knots"), Dim.SPEED, 0.514444)
        add(listOf("°c", "c", "celsius", "grad"), Dim.TEMP, 0.0)
        add(listOf("°f", "f", "fahrenheit"), Dim.TEMP, 0.0)
        add(listOf("k", "kelvin"), Dim.TEMP, 0.0)
    }

    /** Standardziele, wenn keine Zieleinheit angegeben ist. */
    private val defaultTargets = mapOf(
        "km" to listOf("mi"), "m" to listOf("ft"), "cm" to listOf("in", "ft"), "mm" to listOf("in"),
        "mi" to listOf("km"), "ft" to listOf("m", "cm"), "in" to listOf("cm"), "yd" to listOf("m"),
        "kg" to listOf("lb"), "g" to listOf("oz"), "t" to listOf("lb"), "lb" to listOf("kg"), "oz" to listOf("g"),
        "l" to listOf("gal"), "ml" to listOf("l"), "gal" to listOf("l"),
        "km/h" to listOf("mph"), "mph" to listOf("km/h"), "m/s" to listOf("km/h"), "kn" to listOf("km/h"),
        "°c" to listOf("°f"), "°f" to listOf("°c"), "k" to listOf("°c"),
    )

    private val conversionRegex = Regex(
        """^(-?\d+(?:[.,]\d+)?)\s*([a-zäöüß°/]+)(?:\s+(?:in|nach|to|zu|=)\s+([a-zäöüß°/]+))?$""",
        RegexOption.IGNORE_CASE,
    )

    fun conversions(q: String): List<QuickAction.Conversion> {
        val m = conversionRegex.matchEntire(q.trim()) ?: return emptyList()
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return emptyList()
        val from = units[m.groupValues[2].lowercase(Locale.GERMAN)] ?: return emptyList()
        val targetName = m.groupValues[3].lowercase(Locale.GERMAN)
        val targets = if (targetName.isNotEmpty()) {
            listOfNotNull(units[targetName])
        } else {
            defaultTargets[from.name].orEmpty().mapNotNull { units[it] }
        }
        return targets.filter { it.dim == from.dim && it.name != from.name }.map { to ->
            val result = convert(value, from, to)
            val text = "${format(value)} ${from.name} = ${format(result)} ${to.name}"
            QuickAction.Conversion(text, format(result))
        }
    }

    private fun convert(value: Double, from: UnitDef, to: UnitDef): Double {
        if (from.dim != Dim.TEMP) return value * from.factor / to.factor
        val celsius = when (from.name) {
            "°f" -> (value - 32) * 5 / 9
            "k" -> value - 273.15
            else -> value
        }
        return when (to.name) {
            "°f" -> celsius * 9 / 5 + 32
            "k" -> celsius + 273.15
            else -> celsius
        }
    }

    fun format(v: Double): String {
        if (v == Math.floor(v) && Math.abs(v) < 1e12) return v.toLong().toString()
        return BigDecimal(v).round(MathContext(5)).stripTrailingZeros().toPlainString().replace('.', ',')
    }

    // --- Ausführen --------------------------------------------------------------

    fun perform(context: Context, action: QuickAction) {
        val intent = when (action) {
            is QuickAction.OpenUrl -> Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
            is QuickAction.Call -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + action.number))
            is QuickAction.Sms -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + action.number))
            is QuickAction.SystemSetting -> Intent(action.action)
            is QuickAction.Timer -> Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            is QuickAction.Alarm -> Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            is QuickAction.Conversion -> return
        }
        if (!SystemActions.start(context, intent) && action is QuickAction.SystemSetting) {
            SystemActions.start(context, Intent(Settings.ACTION_SETTINGS))
        }
    }
}
