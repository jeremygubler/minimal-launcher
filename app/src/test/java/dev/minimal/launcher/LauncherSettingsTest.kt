package dev.minimal.launcher

import dev.minimal.launcher.data.Favorite
import dev.minimal.launcher.data.FavoritePage
import dev.minimal.launcher.data.MAIN_PAGE
import dev.minimal.launcher.data.PageSchedule
import dev.minimal.launcher.data.PageContext
import dev.minimal.launcher.data.ContextType
import dev.minimal.launcher.data.SearchEngine
import dev.minimal.launcher.data.TaskItem
import dev.minimal.launcher.data.GestureAction
import dev.minimal.launcher.data.HomeFont
import dev.minimal.launcher.data.HomeWeight
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.ThemeMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSettingsTest {
    private val sample = LauncherSettings(
        themeMode = ThemeMode.DARK,
        accent = 0xFF81C995.toInt(),
        showIcons = false,
        iconSize = 44,
        iconPack = "com.example.icons",
        themedIcons = true,
        textScale = 1.1f,
        font = HomeFont.SERIF,
        fontWeight = HomeWeight.LIGHT,
        wallpaperDim = 0.35f,
        alphabetLeft = true,
        eventCount = 3,
        markNewApps = false,
        searchContacts = false,
        searchShortcuts = false,
        showSuggestions = false,
        showBattery = false,
        showEvents = true,
        showMedia = false,
        doubleTap = GestureAction.ASSISTANT,
        swipeUp = GestureAction.DRAWER,
        homePress = GestureAction.SEARCH,
        searchEngine = SearchEngine.DUCKDUCKGO,
        lockedApps = setOf("bank/x#0"),
        appLimits = mapOf("insta/x#0" to 30),
        categoryLimits = mapOf("4" to 60),
        dailyGoalMinutes = 180,
        grayscaleSchedule = PageSchedule(setOf(1, 2, 3, 4, 5, 6, 7), 21 * 60, 7 * 60),
        onboardingDone = true,
        customIcons = mapOf("x/y#0" to "pack:com.icons/whatsapp", "a/b#0" to "file:abc.png"),
        backupFolder = "content://com.android.externalstorage.documents/tree/primary%3ABackups",
        hidden = setOf("a/b#0", "c/d#10"),
        declutterKeep = setOf("keep/me#0"),
        renamed = mapOf("a/b#0" to "Mail"),
        favorites = listOf(
            Favorite("1", listOf("x/y#0"), swipeApp = "z/w#0", swipeLeftShortcut = "compose", swipeLeftLabel = "Neue Nachricht"),
            Favorite("2", listOf("p/q#0", "r/s#0"), name = "Social", page = "work"),
            Favorite("3", listOf("w/x#0"), widgetId = 42),
            Favorite("3", emptyList(), name = "Leer"),
            Favorite("4", emptyList(), name = "Anna", contactUri = "content://com.android.contacts/contacts/lookup/abc/1"),
        ),
        pages = listOf(
            FavoritePage(MAIN_PAGE, "Start"),
            FavoritePage("work", "Arbeit", PageSchedule(setOf(1, 2, 3, 4, 5), 8 * 60, 17 * 60)),
            FavoritePage("car", "Fahren", context = PageContext(ContextType.BLUETOOTH, "Mein Auto")),
            FavoritePage("music", "Musik", context = PageContext(ContextType.HEADPHONES)),
        ),
        currentPage = "work",
        autoPages = true,
        focusApps = setOf("x/y#0"),
        focusManual = true,
        focusPauseSeconds = 12,
        intentionPrompt = true,
        intentionAlways = true,
        eveningRecap = true,
        eveningRecapMinute = 22 * 60 + 30,
        focusSessionStart = 1_000L,
        focusSessionEnd = 2_000L,
        digestEnabled = true,
        digestTimes = listOf(8 * 60, 18 * 60),
        showScreenTime = true,
        showWeather = true,
        showTasks = false,
        tasks = listOf(
            TaskItem("t1", "Milch kaufen"),
            TaskItem("t2", "Zahnarzt", due = java.time.LocalDate.of(2026, 10, 1)),
            TaskItem("t3", "Erledigt", doneAt = 1_790_000_000_000),
        ),
        weatherCity = "Zürich",
        note = "Milch kaufen \"bio\"",
        focusSchedule = PageSchedule(setOf(6, 7), 22 * 60, 7 * 60),
        widgets = listOf(5, 7),
        firstRunDone = true,
    )

    @Test
    fun jsonRoundTripKeepsEverything() {
        val restored = LauncherSettings.fromJson(JSONObject(sample.toJson().toString()))
        assertEquals(sample, restored)
    }

    @Test
    fun importKeepsLocalWidgets() {
        val restored = LauncherSettings.fromJson(sample.toJson(), keepWidgets = listOf(99))
        assertEquals(listOf(99), restored.widgets)
        // Pop-up-Widgets eines anderen Geräts sind hier ungültig.
        assertEquals(null, restored.favorites.first { it.id == "3" }.widgetId)
    }

    @Test
    fun missingOrUnknownValuesFallBackToDefaults() {
        val restored = LauncherSettings.fromJson(JSONObject("""{"themeMode":"PURPLE","iconPack":null}"""))
        assertEquals(LauncherSettings(), restored)
    }

    @Test
    fun oldBackupsWithoutPagesLandOnStartPage() {
        val json = """{"favorites":[{"id":"1","apps":["x/y#0"]}]}"""
        val restored = LauncherSettings.fromJson(JSONObject(json))
        assertEquals(MAIN_PAGE, restored.favorites.single().page)
        assertEquals(listOf(FavoritePage(MAIN_PAGE, "Start")), restored.pages)
        assertEquals(1, restored.pageFavorites().size)
    }

    @Test
    fun unknownCurrentPageFallsBackToFirst() {
        val s = LauncherSettings(currentPage = "gelöscht")
        assertEquals(MAIN_PAGE, s.activePage)
    }

    @Test
    fun favoritesArePerPage() {
        val work = sample.copy(currentPage = "work")
        assertTrue(work.pageFavorites().all { it.page == "work" })
        assertFalse(work.isFavorite("x/y#0"))
        assertTrue(sample.copy(currentPage = MAIN_PAGE).isFavorite("x/y#0"))
    }

    @Test
    fun existingUsersSkipOnboarding() {
        assertTrue(LauncherSettings.fromJson(JSONObject("""{"firstRunDone":true}""")).onboardingDone)
        assertFalse(LauncherSettings.fromJson(JSONObject("{}")).onboardingDone)
    }

    @Test
    fun emptyNamedFolderStaysAFolder() {
        assertTrue(Favorite("f", emptyList(), name = "Leer").isFolder)
        assertFalse(Favorite("c", emptyList(), name = "Anna", contactUri = "content://x").isFolder)
        assertTrue(Favorite("c", emptyList(), name = "Anna", contactUri = "content://x").isContact)
        assertFalse(Favorite("a", listOf("x")).isFolder)
        val start = sample.copy(currentPage = MAIN_PAGE)
        assertTrue(start.isFavorite("x/y#0"))
        assertFalse(start.isFavorite("p/q#0"))
    }
}
