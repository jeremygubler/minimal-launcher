package dev.minimal.launcher.data

import dev.minimal.launcher.util.tr

/** Momentaner Gerätekontext, aus dem Seitenregeln abgeleitet werden. */
data class ContextState(
    val headphones: Boolean = false,
    val charging: Boolean = false,
    /** Namen der gerade verbundenen Bluetooth-Geräte. */
    val bluetooth: Set<String> = emptySet(),
    /** Name des verbundenen WLANs (nur mit Standortberechtigung lesbar). */
    val wifi: String? = null,
)

enum class ContextType(private val de: String, private val en: String) {
    HEADPHONES("Kopfhörer verbunden", "Headphones connected"),
    CHARGING("Lädt", "Charging"),
    BLUETOOTH("Bluetooth-Gerät verbunden", "Bluetooth device connected"),
    WIFI("Mit WLAN verbunden", "Connected to Wi-Fi");

    val label: String get() = tr(de, en)
}

/** Kontextregel einer Favoriten-Seite, z. B. „Bluetooth: Mein Auto“. */
data class PageContext(val type: ContextType, val value: String? = null) {
    fun matches(state: ContextState): Boolean = when (type) {
        ContextType.HEADPHONES -> state.headphones
        ContextType.CHARGING -> state.charging
        ContextType.BLUETOOTH -> !value.isNullOrBlank() && state.bluetooth.any { it.equals(value, ignoreCase = true) }
        ContextType.WIFI -> !value.isNullOrBlank() && state.wifi.equals(value, ignoreCase = true)
    }

    fun describe(): String = when (type) {
        ContextType.BLUETOOTH -> "Bluetooth: ${value ?: "?"}"
        ContextType.WIFI -> tr("WLAN: ${value ?: "?"}", "Wi-Fi: ${value ?: "?"}")
        else -> type.label
    }
}
