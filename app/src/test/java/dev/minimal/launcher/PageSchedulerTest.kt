package dev.minimal.launcher

import dev.minimal.launcher.data.FavoritePage
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.MAIN_PAGE
import dev.minimal.launcher.data.PageSchedule
import dev.minimal.launcher.data.PageScheduler
import dev.minimal.launcher.data.PageContext
import dev.minimal.launcher.data.ContextType
import dev.minimal.launcher.data.ContextState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PageSchedulerTest {
    private val weekdays = setOf(1, 2, 3, 4, 5)
    private val work = PageSchedule(weekdays, 8 * 60, 17 * 60)
    private val night = PageSchedule(setOf(1, 2, 3, 4, 5, 6, 7), 22 * 60, 6 * 60)

    // 2026-09-28 ist ein Montag.
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 9, 28, hour, minute).plusDays(day - 1L)

    @Test
    fun dayWindow() {
        assertTrue(work.matches(at(1, 8)))
        assertTrue(work.matches(at(5, 16, 59)))
        assertFalse(work.matches(at(1, 17)))
        assertFalse(work.matches(at(1, 7, 59)))
        assertFalse(work.matches(at(6, 10))) // Samstag
    }

    @Test
    fun overnightWindow() {
        assertTrue(night.matches(at(1, 23)))
        assertTrue(night.matches(at(2, 5, 30)))
        assertFalse(night.matches(at(2, 12)))
        // Freitagnacht bis Samstagmorgen gehört zum Freitag.
        val fridayNight = PageSchedule(setOf(5), 22 * 60, 6 * 60)
        assertTrue(fridayNight.matches(at(6, 2)))
        assertFalse(fridayNight.matches(at(5, 2)))
    }

    @Test
    fun picksScheduledPageOtherwiseDefault() {
        val settings = LauncherSettings(
            pages = listOf(
                FavoritePage(MAIN_PAGE, "Start"),
                FavoritePage("work", "Arbeit", work),
            ),
        )
        assertEquals("work", PageScheduler.pageFor(settings, at(3, 10)))
        assertEquals(MAIN_PAGE, PageScheduler.pageFor(settings, at(3, 20)))
        assertEquals(MAIN_PAGE, PageScheduler.pageFor(settings, at(7, 10)))
    }

    @Test
    fun describesSchedules() {
        assertEquals("Mo–Fr 08:00–17:00", work.describe())
        assertEquals("Täglich 22:00–06:00", night.describe())
        assertEquals("Sa, So 10:00–12:00", PageSchedule(setOf(6, 7), 600, 720).describe())
    }
}

class PageContextTest {
    private val monday10 = LocalDateTime.of(2026, 9, 28, 10, 0)
    private val work = PageSchedule(setOf(1, 2, 3, 4, 5), 8 * 60, 17 * 60)
    private val settings = LauncherSettings(
        pages = listOf(
            FavoritePage(MAIN_PAGE, "Start"),
            FavoritePage("work", "Arbeit", schedule = work),
            FavoritePage("car", "Fahren", context = PageContext(ContextType.BLUETOOTH, "Mein Auto")),
            FavoritePage("music", "Musik", context = PageContext(ContextType.HEADPHONES)),
            FavoritePage("office", "Büro", context = PageContext(ContextType.WIFI, "Firma-WLAN")),
        ),
    )

    @Test
    fun contextBeatsSchedule() {
        val ctx = ContextState(headphones = true)
        assertEquals("music", PageScheduler.pageFor(settings, monday10, ctx))
    }

    @Test
    fun firstMatchingContextWins() {
        val ctx = ContextState(headphones = true, bluetooth = setOf("mein auto"))
        assertEquals("car", PageScheduler.pageFor(settings, monday10, ctx))
    }

    @Test
    fun wifiMatchesIgnoringCase() {
        assertEquals("office", PageScheduler.pageFor(settings, monday10, ContextState(wifi = "firma-wlan")))
        assertEquals("work", PageScheduler.pageFor(settings, monday10, ContextState(wifi = "Zuhause")))
    }

    @Test
    fun defaultIsFirstPageWithoutRules() {
        val evening = LocalDateTime.of(2026, 9, 28, 20, 0)
        assertEquals(MAIN_PAGE, PageScheduler.pageFor(settings, evening, ContextState()))
    }

    @Test
    fun rulesWithoutValueNeverMatch() {
        assertFalse(PageContext(ContextType.BLUETOOTH, null).matches(ContextState(bluetooth = setOf("x"))))
        assertTrue(PageContext(ContextType.CHARGING).matches(ContextState(charging = true)))
    }
}
