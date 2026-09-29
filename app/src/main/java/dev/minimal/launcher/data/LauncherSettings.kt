package dev.minimal.launcher.data

import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class GestureAction(val label: String) {
    NONE("Keine Aktion"),
    NOTIFICATIONS("Benachrichtigungen öffnen"),
    QUICK_SETTINGS("Schnelleinstellungen öffnen"),
    SEARCH("Suche öffnen"),
    DRAWER("Alle Apps öffnen"),
    LOCK("Bildschirm sperren"),
}

/** Ein Favorit ist entweder eine einzelne App oder ein Ordner (mehrere Apps). */
data class Favorite(
    val id: String,
    val apps: List<String>,
    val name: String? = null,
    /** App, die beim Wischen nach rechts über den Favoriten geöffnet wird. */
    val swipeApp: String? = null,
) {
    val isFolder: Boolean get() = apps.size > 1 || name != null
}

data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 0 = Systemfarbe (Material You). */
    val accent: Int = 0,
    val showIcons: Boolean = true,
    val iconSize: Int = 36,
    val iconPack: String? = null,
    val textScale: Float = 1f,
    val wallpaperDim: Float = 0.2f,
    val blur: Boolean = true,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showAlarm: Boolean = true,
    val alphabetLeft: Boolean = false,
    val notificationDots: Boolean = true,
    val notificationPreview: Boolean = true,
    val autoKeyboard: Boolean = true,
    val doubleTap: GestureAction = GestureAction.LOCK,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val swipeUp: GestureAction = GestureAction.SEARCH,
    val hidden: Set<String> = emptySet(),
    val renamed: Map<String, String> = emptyMap(),
    val favorites: List<Favorite> = emptyList(),
    val widgets: List<Int> = emptyList(),
    val firstRunDone: Boolean = false,
) {
    fun isFavorite(key: String) = favorites.any { !it.isFolder && it.apps.firstOrNull() == key }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("themeMode", themeMode.name)
        put("accent", accent)
        put("showIcons", showIcons)
        put("iconSize", iconSize)
        put("iconPack", iconPack ?: JSONObject.NULL)
        put("textScale", textScale.toDouble())
        put("wallpaperDim", wallpaperDim.toDouble())
        put("blur", blur)
        put("showClock", showClock)
        put("showDate", showDate)
        put("showAlarm", showAlarm)
        put("alphabetLeft", alphabetLeft)
        put("notificationDots", notificationDots)
        put("notificationPreview", notificationPreview)
        put("autoKeyboard", autoKeyboard)
        put("doubleTap", doubleTap.name)
        put("swipeDown", swipeDown.name)
        put("swipeUp", swipeUp.name)
        put("hidden", JSONArray(hidden.toList()))
        put("renamed", JSONObject(renamed as Map<*, *>))
        put("favorites", JSONArray().apply {
            favorites.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id)
                    put("apps", JSONArray(f.apps))
                    put("name", f.name ?: JSONObject.NULL)
                    put("swipeApp", f.swipeApp ?: JSONObject.NULL)
                })
            }
        })
        put("widgets", JSONArray(widgets))
        put("firstRunDone", firstRunDone)
    }

    companion object {
        fun fromJson(o: JSONObject, keepWidgets: List<Int>? = null): LauncherSettings {
            val d = LauncherSettings()
            fun str(k: String) = if (o.isNull(k)) null else o.optString(k)
            return LauncherSettings(
                themeMode = enumOr(str("themeMode"), d.themeMode),
                accent = o.optInt("accent", d.accent),
                showIcons = o.optBoolean("showIcons", d.showIcons),
                iconSize = o.optInt("iconSize", d.iconSize),
                iconPack = str("iconPack"),
                textScale = o.optDouble("textScale", d.textScale.toDouble()).toFloat(),
                wallpaperDim = o.optDouble("wallpaperDim", d.wallpaperDim.toDouble()).toFloat(),
                blur = o.optBoolean("blur", d.blur),
                showClock = o.optBoolean("showClock", d.showClock),
                showDate = o.optBoolean("showDate", d.showDate),
                showAlarm = o.optBoolean("showAlarm", d.showAlarm),
                alphabetLeft = o.optBoolean("alphabetLeft", d.alphabetLeft),
                notificationDots = o.optBoolean("notificationDots", d.notificationDots),
                notificationPreview = o.optBoolean("notificationPreview", d.notificationPreview),
                autoKeyboard = o.optBoolean("autoKeyboard", d.autoKeyboard),
                doubleTap = enumOr(str("doubleTap"), d.doubleTap),
                swipeDown = enumOr(str("swipeDown"), d.swipeDown),
                swipeUp = enumOr(str("swipeUp"), d.swipeUp),
                hidden = o.optJSONArray("hidden")?.strings()?.toSet() ?: emptySet(),
                renamed = o.optJSONObject("renamed")?.let { r ->
                    r.keys().asSequence().associateWith { r.getString(it) }
                } ?: emptyMap(),
                favorites = o.optJSONArray("favorites")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val f = arr.optJSONObject(i) ?: return@mapNotNull null
                        Favorite(
                            id = f.optString("id"),
                            apps = f.optJSONArray("apps")?.strings() ?: emptyList(),
                            name = if (f.isNull("name")) null else f.optString("name"),
                            swipeApp = if (f.isNull("swipeApp")) null else f.optString("swipeApp"),
                        )
                    }
                } ?: emptyList(),
                widgets = keepWidgets ?: o.optJSONArray("widgets")?.let { arr ->
                    (0 until arr.length()).map { arr.getInt(it) }
                } ?: emptyList(),
                firstRunDone = o.optBoolean("firstRunDone", false),
            )
        }

        private fun JSONArray.strings() = (0 until length()).map { getString(it) }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
    }
}
