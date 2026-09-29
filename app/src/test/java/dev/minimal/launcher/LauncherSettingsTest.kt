package dev.minimal.launcher

import dev.minimal.launcher.data.Favorite
import dev.minimal.launcher.data.GestureAction
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
        textScale = 1.1f,
        wallpaperDim = 0.35f,
        alphabetLeft = true,
        searchContacts = false,
        showEvents = true,
        showMedia = false,
        doubleTap = GestureAction.NONE,
        swipeUp = GestureAction.DRAWER,
        hidden = setOf("a/b#0", "c/d#10"),
        renamed = mapOf("a/b#0" to "Mail"),
        favorites = listOf(
            Favorite("1", listOf("x/y#0"), swipeApp = "z/w#0"),
            Favorite("2", listOf("p/q#0", "r/s#0"), name = "Social"),
            Favorite("3", emptyList(), name = "Leer"),
        ),
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
    }

    @Test
    fun missingOrUnknownValuesFallBackToDefaults() {
        val restored = LauncherSettings.fromJson(JSONObject("""{"themeMode":"PURPLE","iconPack":null}"""))
        assertEquals(LauncherSettings(), restored)
    }

    @Test
    fun emptyNamedFolderStaysAFolder() {
        assertTrue(Favorite("f", emptyList(), name = "Leer").isFolder)
        assertFalse(Favorite("a", listOf("x")).isFolder)
        assertTrue(sample.isFavorite("x/y#0"))
        assertFalse(sample.isFavorite("p/q#0"))
    }
}
