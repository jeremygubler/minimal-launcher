package dev.minimal.launcher

import dev.minimal.launcher.data.EveningRecap
import dev.minimal.launcher.data.GestureAction
import dev.minimal.launcher.data.IntentionStats
import dev.minimal.launcher.data.PageSchedule
import dev.minimal.launcher.data.RecapInput
import dev.minimal.launcher.data.Tasks
import dev.minimal.launcher.util.tr
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Englische Oberfläche auf nicht-deutschen Systemen. */
class LangTest {
    private lateinit var saved: Locale

    @Before
    fun english() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restore() = Locale.setDefault(saved)

    @Test
    fun picksEnglishOutsideGerman() {
        assertEquals("Lock screen", GestureAction.LOCK.label)
        assertEquals("Daily 22:00–06:00", PageSchedule((1..7).toSet(), 22 * 60, 6 * 60).describe())
        assertEquals("Mo–Fr 08:00–17:00", PageSchedule((1..5).toSet(), 8 * 60, 17 * 60).describe())
        assertEquals("Boredom", IntentionStats.BOREDOM)
        val (title, text) = EveningRecap.compose(RecapInput(90 * 60_000L, null, 0, 0, null, 2, 1, 0))
        assertEquals("Your day · 1 h 30 min screen time", title)
        assertTrue(text.contains("2× opened mindfully · 1× skipped"))
    }

    @Test
    fun swissGermanStaysGerman() {
        Locale.setDefault(Locale.forLanguageTag("de-CH"))
        assertEquals("Bildschirm sperren", GestureAction.LOCK.label)
        assertEquals("Hallo", tr("Hallo", "Hello"))
    }

    @Test
    fun boredomCountsInBothLanguages() {
        assertTrue(IntentionStats.isBoredom("Langeweile"))
        assertTrue(IntentionStats.isBoredom(" boredom"))
    }

    @Test
    fun englishDueWords() {
        val today = LocalDate.of(2026, 9, 30)
        assertEquals("buy milk" to today.plusDays(1), Tasks.parseCommand("todo buy milk tomorrow", today))
        assertEquals("call mom" to today.plusDays(2), Tasks.parseCommand("task call mom day after tomorrow", today))
        assertEquals("laundry" to today, Tasks.parseCommand("todo today laundry", today))
    }
}
