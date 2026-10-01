package dev.minimal.launcher

import dev.minimal.launcher.data.DailyIntention
import dev.minimal.launcher.data.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyIntentionTest {
    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun validOnlyOnItsDay() {
        val s = DailyIntention.set(LauncherSettings(dailyIntentionEnabled = true), "  Präsentation  ", today)
        assertEquals("Präsentation", DailyIntention.today(s, today))
        assertEquals(null, DailyIntention.today(s, today.plusDays(1)))
        assertFalse(DailyIntention.shouldAsk(s, today, pro = true))
        assertTrue(DailyIntention.shouldAsk(s, today.plusDays(1), pro = true))
    }

    @Test
    fun onlyWhenEnabledAndPro() {
        assertFalse(DailyIntention.shouldAsk(LauncherSettings(), today, pro = true))
        assertFalse(DailyIntention.shouldAsk(LauncherSettings(dailyIntentionEnabled = true), today, pro = false))
    }

    @Test
    fun settingResetsDone() {
        val s = DailyIntention.set(LauncherSettings(dailyIntentionDone = true), "Neu", today)
        assertFalse(s.dailyIntentionDone)
    }
}
