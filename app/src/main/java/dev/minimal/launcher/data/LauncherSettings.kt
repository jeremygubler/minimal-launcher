package dev.minimal.launcher.data

import dev.minimal.launcher.util.tr
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SearchEngine(private val de: String, private val en: String, val url: String?) {
    SYSTEM("Standard-App des Systems", "System default app", null),
    GOOGLE("Google", "Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    STARTPAGE("Startpage", "Startpage", "https://www.startpage.com/do/search?q="),
    ECOSIA("Ecosia", "Ecosia", "https://www.ecosia.org/search?q="),
    BING("Bing", "Bing", "https://www.bing.com/search?q=");

    val label: String get() = tr(de, en)
}

enum class HomeFont(private val de: String, private val en: String, val pro: Boolean = false) {
    SYSTEM("System", "System"), SERIF("Serif", "Serif"), MONO("Monospace", "Monospace"), CURSIVE("Handschrift", "Handwriting"),
    /** Mitgelieferte Schriften (Pro). */
    KANSO_SERIF("Cormorant (Kanso)", "Cormorant (Kanso)", pro = true),
    KANSO_SANS("Inter (Kanso)", "Inter (Kanso)", pro = true);

    val label: String get() = tr(de, en)
}

enum class HomeWeight(private val de: String, private val en: String) {
    LIGHT("Leicht", "Light"), NORMAL("Normal", "Regular"), MEDIUM("Kräftig", "Bold");

    val label: String get() = tr(de, en)
}

enum class GestureAction(private val de: String, private val en: String) {
    NONE("Keine Aktion", "No action"),
    NOTIFICATIONS("Benachrichtigungen öffnen", "Open notifications"),
    QUICK_SETTINGS("Schnelleinstellungen öffnen", "Open quick settings"),
    SEARCH("Suche öffnen", "Open search"),
    DRAWER("Alle Apps öffnen", "Open all apps"),
    LOCK("Bildschirm sperren", "Lock screen"),
    ASSISTANT("Gemini / Assistant öffnen", "Open Gemini / Assistant");

    val label: String get() = tr(de, en)
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
    /** Kontakt-Favorit: Lookup-URI des Kontakts (Name steht in [name]). */
    val contactUri: String? = null,
    /** Pop-up-Widget, das beim Wischen nach rechts erscheint (ersetzt [swipeApp]). Gerätespezifisch. */
    val widgetId: Int? = null,
) {
    val isFolder: Boolean get() = contactUri == null && (apps.size > 1 || name != null)
    val isContact: Boolean get() = contactUri != null
}

const val MAIN_PAGE = "main"

/** Eine Favoriten-Seite, z. B. „Start“, „Arbeit“, „Privat“. */
data class FavoritePage(
    val id: String,
    val name: String,
    val schedule: PageSchedule? = null,
    /** Kontextregel (hat Vorrang vor dem Zeitplan). */
    val context: PageContext? = null,
)

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
    /** Anzahl der angezeigten Termine (1–3). */
    val eventCount: Int = 1,
    /** Neu installierte Apps in der Liste markieren. */
    val markNewApps: Boolean = true,
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
    /** Home-Taste, während der Startbildschirm schon offen ist. */
    val homePress: GestureAction = GestureAction.NONE,
    val searchEngine: SearchEngine = SearchEngine.SYSTEM,
    /** Apps, die nur nach Fingerabdruck/PIN geöffnet werden. */
    val lockedApps: Set<String> = emptySet(),
    /** Tageslimit pro App in Minuten. */
    val appLimits: Map<String, Int> = emptyMap(),
    /** Tageslimit pro App-Kategorie (Kategorie-Nummer als Text) in Minuten. */
    val categoryLimits: Map<String, Int> = emptyMap(),
    /** Tagesziel für die gesamte Bildschirmzeit in Minuten (0 = aus). */
    val dailyGoalMinutes: Int = 0,
    /** Zeitplan, in dem Icons in Graustufen erscheinen. */
    val grayscaleSchedule: PageSchedule? = null,
    val onboardingDone: Boolean = false,
    /** Eigene Icons: App-Schlüssel → "pack:<paket>/<name>" oder "file:<datei>". */
    val customIcons: Map<String, String> = emptyMap(),
    /** Ordner (Tree-URI) für die automatische Sicherung. */
    val backupFolder: String? = null,
    val hidden: Set<String> = emptySet(),
    /** Apps, die beim Aufräumen bewusst behalten werden (nicht mehr vorschlagen). */
    val declutterKeep: Set<String> = emptySet(),
    val renamed: Map<String, String> = emptyMap(),
    val favorites: List<Favorite> = emptyList(),
    val pages: List<FavoritePage> = listOf(FavoritePage(MAIN_PAGE, tr("Start", "Home"))),
    val currentPage: String = MAIN_PAGE,
    val autoPages: Boolean = false,
    /** Als ablenkend markierte Apps. */
    val focusApps: Set<String> = emptySet(),
    val focusManual: Boolean = false,
    val focusSchedule: PageSchedule? = null,
    /** Dauer der Denkpause im Fokus-Modus in Sekunden (0 = keine). */
    val focusPauseSeconds: Int = 5,
    /** Vor ablenkenden Apps nach der Absicht fragen (statt nur einer Denkpause). */
    val intentionPrompt: Boolean = false,
    /** Absichtsfrage auch ausserhalb des Fokus-Modus stellen. */
    val intentionAlways: Boolean = false,
    /** Abendrückblick als Benachrichtigung, täglich um [eveningRecapMinute] (Minute des Tages). */
    val eveningRecap: Boolean = false,
    val eveningRecapMinute: Int = 21 * 60,
    /** Laufende Fokus-Sitzung (Epoch-Millis, 0 = keine). */
    val focusSessionStart: Long = 0L,
    val focusSessionEnd: Long = 0L,
    /** Benachrichtigungen ablenkender Apps zurückhalten und zu [digestTimes] gesammelt zustellen. */
    val digestEnabled: Boolean = false,
    val digestTimes: List<Int> = listOf(12 * 60, 18 * 60),
    /** Kanso-Stil: Volltonhintergrund statt Hintergrundbild (Pro). */
    val kansoStyle: KansoStyle = KansoStyle.NONE,
    /** Feiner Ensō im Stil-Hintergrund. */
    val kansoEnso: Boolean = true,
    val showScreenTime: Boolean = false,
    /** Wetter ist opt-in: nur dann greift der Launcher aufs Internet zu. */
    val showWeather: Boolean = false,
    /** Fester Ort fürs Wetter; leer = ungefährer Standort. */
    val weatherCity: String = "",
    /** Schnellnotiz auf dem Startbildschirm. */
    val note: String = "",
    /** Eingebaute Aufgabenliste. */
    val tasks: List<TaskItem> = emptyList(),
    val showTasks: Boolean = true,
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
        put("eventCount", eventCount)
        put("markNewApps", markNewApps)
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
        put("homePress", homePress.name)
        put("searchEngine", searchEngine.name)
        put("lockedApps", JSONArray(lockedApps.toList()))
        put("declutterKeep", JSONArray(declutterKeep.toList()))
        put("appLimits", JSONObject(appLimits as Map<*, *>))
        put("categoryLimits", JSONObject(categoryLimits as Map<*, *>))
        put("dailyGoalMinutes", dailyGoalMinutes)
        grayscaleSchedule?.let { put("grayscaleSchedule", scheduleToJson(it)) }
        put("onboardingDone", onboardingDone)
        put("customIcons", JSONObject(customIcons as Map<*, *>))
        backupFolder?.let { put("backupFolder", it) }
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
                    f.contactUri?.let { put("contactUri", it) }
                    f.widgetId?.let { put("widgetId", it) }
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
                    p.context?.let { c ->
                        put("context", JSONObject().apply {
                            put("type", c.type.name)
                            c.value?.let { put("value", it) }
                        })
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
        put("intentionPrompt", intentionPrompt)
        put("intentionAlways", intentionAlways)
        put("eveningRecap", eveningRecap)
        put("eveningRecapMinute", eveningRecapMinute)
        put("focusSessionStart", focusSessionStart)
        put("focusSessionEnd", focusSessionEnd)
        put("digestEnabled", digestEnabled)
        put("digestTimes", JSONArray(digestTimes))
        put("kansoStyle", kansoStyle.name)
        put("kansoEnso", kansoEnso)
        put("showScreenTime", showScreenTime)
        put("showWeather", showWeather)
        put("weatherCity", weatherCity)
        put("note", note)
        put("showTasks", showTasks)
        put("tasks", JSONArray().apply {
            tasks.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    t.due?.let { put("due", it.toString()) }
                    t.doneAt?.let { put("doneAt", it) }
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
                themedIcons = o.optBoolean("themedIcons", d.themedIcons),
                textScale = o.optDouble("textScale", d.textScale.toDouble()).toFloat(),
                font = enumOr(str("font"), d.font),
                fontWeight = enumOr(str("fontWeight"), d.fontWeight),
                wallpaperDim = o.optDouble("wallpaperDim", d.wallpaperDim.toDouble()).toFloat(),
                blur = o.optBoolean("blur", d.blur),
                showClock = o.optBoolean("showClock", d.showClock),
                showDate = o.optBoolean("showDate", d.showDate),
                showAlarm = o.optBoolean("showAlarm", d.showAlarm),
                eventCount = o.optInt("eventCount", d.eventCount).coerceIn(1, 3),
                markNewApps = o.optBoolean("markNewApps", d.markNewApps),
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
                homePress = enumOr(str("homePress"), d.homePress),
                searchEngine = enumOr(str("searchEngine"), d.searchEngine),
                lockedApps = o.optJSONArray("lockedApps")?.strings()?.toSet() ?: emptySet(),
                declutterKeep = o.optJSONArray("declutterKeep")?.strings()?.toSet() ?: emptySet(),
                appLimits = o.optJSONObject("appLimits")?.let { l ->
                    l.keys().asSequence().associateWith { l.optInt(it) }.filterValues { it > 0 }
                } ?: emptyMap(),
                categoryLimits = o.optJSONObject("categoryLimits")?.let { l ->
                    l.keys().asSequence().associateWith { l.optInt(it) }.filterValues { it > 0 }
                } ?: emptyMap(),
                dailyGoalMinutes = o.optInt("dailyGoalMinutes", 0).coerceIn(0, 24 * 60),
                grayscaleSchedule = o.optJSONObject("grayscaleSchedule")?.let(::scheduleFromJson),
                // Wer den Launcher schon nutzt, bekommt den Assistenten nicht nachträglich.
                onboardingDone = o.optBoolean("onboardingDone", o.optBoolean("firstRunDone", false)),
                customIcons = o.optJSONObject("customIcons")?.let { c ->
                    c.keys().asSequence().associateWith { c.getString(it) }
                } ?: emptyMap(),
                backupFolder = o.optString("backupFolder").ifEmpty { null },
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
                            contactUri = f.optString("contactUri").ifEmpty { null },
                            // Widget-IDs gelten nur auf diesem Gerät – beim Import verwerfen.
                            widgetId = if (keepWidgets == null && f.has("widgetId")) f.optInt("widgetId") else null,
                        )
                    }
                } ?: emptyList(),
                pages = o.optJSONArray("pages")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val p = arr.optJSONObject(i) ?: return@mapNotNull null
                        FavoritePage(
                            id = p.optString("id").ifEmpty { return@mapNotNull null },
                            name = p.optString("name", tr("Seite", "Page")),
                            schedule = p.optJSONObject("schedule")?.let(::scheduleFromJson),
                            context = p.optJSONObject("context")?.let { c ->
                                enumValues<ContextType>().firstOrNull { it.name == c.optString("type") }
                                    ?.let { PageContext(it, c.optString("value").ifEmpty { null }) }
                            },
                        )
                    }
                }?.takeIf { it.isNotEmpty() } ?: d.pages,
                currentPage = o.optString("currentPage", MAIN_PAGE).ifEmpty { MAIN_PAGE },
                autoPages = o.optBoolean("autoPages", d.autoPages),
                focusApps = o.optJSONArray("focusApps")?.strings()?.toSet() ?: emptySet(),
                focusManual = o.optBoolean("focusManual", false),
                focusSchedule = o.optJSONObject("focusSchedule")?.let(::scheduleFromJson),
                focusPauseSeconds = o.optInt("focusPauseSeconds", d.focusPauseSeconds).coerceIn(0, 60),
                intentionPrompt = o.optBoolean("intentionPrompt", d.intentionPrompt),
                intentionAlways = o.optBoolean("intentionAlways", d.intentionAlways),
                eveningRecap = o.optBoolean("eveningRecap", d.eveningRecap),
                eveningRecapMinute = o.optInt("eveningRecapMinute", d.eveningRecapMinute).coerceIn(0, 24 * 60 - 1),
                // Eine laufende Sitzung gehört zum Gerät – beim Import nicht übernehmen.
                focusSessionStart = if (keepWidgets == null) o.optLong("focusSessionStart", 0L) else 0L,
                focusSessionEnd = if (keepWidgets == null) o.optLong("focusSessionEnd", 0L) else 0L,
                digestEnabled = o.optBoolean("digestEnabled", d.digestEnabled),
                kansoStyle = enumOr(str("kansoStyle"), d.kansoStyle),
                kansoEnso = o.optBoolean("kansoEnso", d.kansoEnso),
                digestTimes = o.optJSONArray("digestTimes")?.let { a ->
                    (0 until a.length()).map { a.optInt(it) }.filter { it in 0 until 24 * 60 }.distinct().sorted()
                }?.takeIf { it.isNotEmpty() } ?: d.digestTimes,
                showScreenTime = o.optBoolean("showScreenTime", d.showScreenTime),
                showWeather = o.optBoolean("showWeather", d.showWeather),
                weatherCity = o.optString("weatherCity", ""),
                note = o.optString("note", ""),
                showTasks = o.optBoolean("showTasks", d.showTasks),
                tasks = o.optJSONArray("tasks")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val t = arr.optJSONObject(i) ?: return@mapNotNull null
                        TaskItem(
                            id = t.optString("id").ifEmpty { return@mapNotNull null },
                            title = t.optString("title"),
                            due = t.optString("due").ifEmpty { null }?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                            doneAt = if (t.has("doneAt")) t.optLong("doneAt") else null,
                        )
                    }
                } ?: emptyList(),
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
