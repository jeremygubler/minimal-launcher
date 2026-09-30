package dev.minimal.launcher.data

import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class HomeFont(val label: String) { SYSTEM("System"), SERIF("Serif"), MONO("Monospace"), CURSIVE("Handschrift") }

enum class HomeWeight(val label: String) { LIGHT("Leicht"), NORMAL("Normal"), MEDIUM("Kräftig") }

enum class GestureAction(val label: String) {
    NONE("Keine Aktion"),
    NOTIFICATIONS("Benachrichtigungen öffnen"),
    QUICK_SETTINGS("Schnelleinstellungen öffnen"),
    SEARCH("Suche öffnen"),
    DRAWER("Alle Apps öffnen"),
    LOCK("Bildschirm sperren"),
    ASSISTANT("Gemini / Assistant öffnen"),
}

/** Ein Favorit ist entweder eine einzelne App oder ein Ordner (mehrere Apps). */
data class Favorite(
    val id: String,
    val apps: List<String>,
    val name: String? = null,
    /** App, die beim Wischen nach rechts über den Favoriten geöffnet wird. */
    val swipeApp: String? = null,
    /** Seite, auf der der Favorit liegt. */
    val page: String = MAIN_PAGE,
    /** App-Shortcut (ID), der beim Wischen nach links ausgeführt wird, samt Anzeigename. */
    val swipeLeftShortcut: String? = null,
    val swipeLeftLabel: String? = null,
) {
    val isFolder: Boolean get() = apps.size > 1 || name != null
}

const val MAIN_PAGE = "main"

/** Eine Favoriten-Seite, z. B. „Start“, „Arbeit“, „Privat“. */
data class FavoritePage(val id: String, val name: String, val schedule: PageSchedule? = null)

data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 0 = Systemfarbe (Material You). */
    val accent: Int = 0,
    val showIcons: Boolean = true,
    val iconSize: Int = 36,
    val iconPack: String? = null,
    val themedIcons: Boolean = false,
    val textScale: Float = 1f,
    val font: HomeFont = HomeFont.SYSTEM,
    val fontWeight: HomeWeight = HomeWeight.NORMAL,
    val wallpaperDim: Float = 0.2f,
    val blur: Boolean = true,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showAlarm: Boolean = true,
    val showEvents: Boolean = false,
    val showMedia: Boolean = true,
    val alphabetLeft: Boolean = false,
    val notificationDots: Boolean = true,
    val notificationPreview: Boolean = true,
    val autoKeyboard: Boolean = true,
    val searchContacts: Boolean = true,
    val searchShortcuts: Boolean = true,
    val showSuggestions: Boolean = true,
    val showBattery: Boolean = true,
    val doubleTap: GestureAction = GestureAction.LOCK,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val swipeUp: GestureAction = GestureAction.SEARCH,
    val hidden: Set<String> = emptySet(),
    val renamed: Map<String, String> = emptyMap(),
    val favorites: List<Favorite> = emptyList(),
    val pages: List<FavoritePage> = listOf(FavoritePage(MAIN_PAGE, "Start")),
    val currentPage: String = MAIN_PAGE,
    val autoPages: Boolean = false,
    /** Als ablenkend markierte Apps. */
    val focusApps: Set<String> = emptySet(),
    val focusManual: Boolean = false,
    val focusSchedule: PageSchedule? = null,
    /** Dauer der Denkpause im Fokus-Modus in Sekunden (0 = keine). */
    val focusPauseSeconds: Int = 5,
    val showScreenTime: Boolean = false,
    /** Schnellnotiz auf dem Startbildschirm. */
    val note: String = "",
    val widgets: List<Int> = emptyList(),
    val firstRunDone: Boolean = false,
) {
    /** Die angezeigte Seite – fällt auf die erste zurück, falls die gespeicherte nicht mehr existiert. */
    val activePage: String get() = pages.firstOrNull { it.id == currentPage }?.id ?: pages.first().id

    /** Favoriten einer Seite (standardmäßig der aktuellen). */
    fun pageFavorites(page: String = activePage) = favorites.filter { it.page == page }

    /** Liegt die App als einzelner Favorit auf der aktuellen Seite? */
    fun isFavorite(key: String) = pageFavorites().any { !it.isFolder && it.apps.firstOrNull() == key }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("themeMode", themeMode.name)
        put("accent", accent)
        put("showIcons", showIcons)
        put("iconSize", iconSize)
        put("iconPack", iconPack ?: JSONObject.NULL)
        put("themedIcons", themedIcons)
        put("textScale", textScale.toDouble())
        put("font", font.name)
        put("fontWeight", fontWeight.name)
        put("wallpaperDim", wallpaperDim.toDouble())
        put("blur", blur)
        put("showClock", showClock)
        put("showDate", showDate)
        put("showAlarm", showAlarm)
        put("showEvents", showEvents)
        put("showMedia", showMedia)
        put("alphabetLeft", alphabetLeft)
        put("notificationDots", notificationDots)
        put("notificationPreview", notificationPreview)
        put("autoKeyboard", autoKeyboard)
        put("searchContacts", searchContacts)
        put("searchShortcuts", searchShortcuts)
        put("showSuggestions", showSuggestions)
        put("showBattery", showBattery)
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
                    put("page", f.page)
                    f.swipeLeftShortcut?.let { put("swipeLeftShortcut", it) }
                    f.swipeLeftLabel?.let { put("swipeLeftLabel", it) }
                })
            }
        })
        put("pages", JSONArray().apply {
            pages.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    p.schedule?.let { sch ->
                        put("schedule", scheduleToJson(sch))
                    }
                })
            }
        })
        put("currentPage", currentPage)
        put("autoPages", autoPages)
        put("focusApps", JSONArray(focusApps.toList()))
        put("focusManual", focusManual)
        focusSchedule?.let { put("focusSchedule", scheduleToJson(it)) }
        put("focusPauseSeconds", focusPauseSeconds)
        put("showScreenTime", showScreenTime)
        put("note", note)
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
                themedIcons = o.optBoolean("themedIcons", d.themedIcons),
                textScale = o.optDouble("textScale", d.textScale.toDouble()).toFloat(),
                font = enumOr(str("font"), d.font),
                fontWeight = enumOr(str("fontWeight"), d.fontWeight),
                wallpaperDim = o.optDouble("wallpaperDim", d.wallpaperDim.toDouble()).toFloat(),
                blur = o.optBoolean("blur", d.blur),
                showClock = o.optBoolean("showClock", d.showClock),
                showDate = o.optBoolean("showDate", d.showDate),
                showAlarm = o.optBoolean("showAlarm", d.showAlarm),
                showEvents = o.optBoolean("showEvents", d.showEvents),
                showMedia = o.optBoolean("showMedia", d.showMedia),
                alphabetLeft = o.optBoolean("alphabetLeft", d.alphabetLeft),
                notificationDots = o.optBoolean("notificationDots", d.notificationDots),
                notificationPreview = o.optBoolean("notificationPreview", d.notificationPreview),
                autoKeyboard = o.optBoolean("autoKeyboard", d.autoKeyboard),
                searchContacts = o.optBoolean("searchContacts", d.searchContacts),
                searchShortcuts = o.optBoolean("searchShortcuts", d.searchShortcuts),
                showSuggestions = o.optBoolean("showSuggestions", d.showSuggestions),
                showBattery = o.optBoolean("showBattery", d.showBattery),
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
                            page = f.optString("page", MAIN_PAGE).ifEmpty { MAIN_PAGE },
                            swipeLeftShortcut = f.optString("swipeLeftShortcut").ifEmpty { null },
                            swipeLeftLabel = f.optString("swipeLeftLabel").ifEmpty { null },
                        )
                    }
                } ?: emptyList(),
                pages = o.optJSONArray("pages")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val p = arr.optJSONObject(i) ?: return@mapNotNull null
                        FavoritePage(
                            id = p.optString("id").ifEmpty { return@mapNotNull null },
                            name = p.optString("name", "Seite"),
                            schedule = p.optJSONObject("schedule")?.let(::scheduleFromJson),
                        )
                    }
                }?.takeIf { it.isNotEmpty() } ?: d.pages,
                currentPage = o.optString("currentPage", MAIN_PAGE).ifEmpty { MAIN_PAGE },
                autoPages = o.optBoolean("autoPages", d.autoPages),
                focusApps = o.optJSONArray("focusApps")?.strings()?.toSet() ?: emptySet(),
                focusManual = o.optBoolean("focusManual", false),
                focusSchedule = o.optJSONObject("focusSchedule")?.let(::scheduleFromJson),
                focusPauseSeconds = o.optInt("focusPauseSeconds", d.focusPauseSeconds).coerceIn(0, 60),
                showScreenTime = o.optBoolean("showScreenTime", d.showScreenTime),
                note = o.optString("note", ""),
                widgets = keepWidgets ?: o.optJSONArray("widgets")?.let { arr ->
                    (0 until arr.length()).map { arr.getInt(it) }
                } ?: emptyList(),
                firstRunDone = o.optBoolean("firstRunDone", false),
            )
        }

        private fun scheduleToJson(sch: PageSchedule) = JSONObject().apply {
            put("days", JSONArray(sch.days.sorted()))
            put("start", sch.start)
            put("end", sch.end)
        }

        private fun scheduleFromJson(sch: JSONObject): PageSchedule {
            val days = sch.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                ?.filter { it in 1..7 }?.toSet().orEmpty()
            return PageSchedule(days, sch.optInt("start", 0).coerceIn(0, 1439), sch.optInt("end", 0).coerceIn(0, 1439))
        }

        private fun JSONArray.strings() = (0 until length()).map { getString(it) }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
    }
}
