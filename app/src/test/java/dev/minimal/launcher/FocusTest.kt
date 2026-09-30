package dev.minimal.launcher

import dev.minimal.launcher.data.Focus
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.PageSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class FocusTest {
    private val monday10 = LocalDateTime.of(2026, 9, 28, 10, 0)
    private val monday20 = LocalDateTime.of(2026, 9, 28, 20, 0)
    private val work = PageSchedule(setOf(1, 2, 3, 4, 5), 9 * 60, 17 * 60)

    @Test
    fun offByDefault() {
        val s = LauncherSettings(focusApps = setOf("insta"))
        assertFalse(Focus.isActive(s, monday10))
        assertFalse(Focus.isBlocked(s, "insta", monday10))
    }

    @Test
    fun manualAlwaysActive() {
        val s = LauncherSettings(focusApps = setOf("insta"), focusManual = true)
        assertTrue(Focus.isBlocked(s, "insta", monday20))
        assertFalse(Focus.isBlocked(s, "mail", monday20))
    }

    @Test
    fun scheduleActiveOnlyInWindow() {
        val s = LauncherSettings(focusApps = setOf("insta"), focusSchedule = work)
        assertTrue(Focus.isBlocked(s, "insta", monday10))
        assertFalse(Focus.isBlocked(s, "insta", monday20))
    }

    @Test
    fun runningSessionActivatesFocus() {
        val zone = java.time.ZoneId.systemDefault()
        val end = monday10.plusMinutes(25).atZone(zone).toInstant().toEpochMilli()
        val s = LauncherSettings(focusApps = setOf("insta"), focusSessionEnd = end)
        assertTrue(Focus.isBlocked(s, "insta", monday10))
        assertFalse(Focus.isActive(s, monday10.plusMinutes(26)))
    }

    @Test
    fun summarizesSessions() {
        val min = 60_000L
        val summary = dev.minimal.launcher.data.FocusSessions.summarize(
            listOf(
                dev.minimal.launcher.data.FocusSessionEntry(100 * min, 125 * min, completed = true),
                dev.minimal.launcher.data.FocusSessionEntry(200 * min, 250 * min, completed = true),
                dev.minimal.launcher.data.FocusSessionEntry(300 * min, 310 * min, completed = false),
                dev.minimal.launcher.data.FocusSessionEntry(1 * min, 26 * min, completed = true),
            ),
            from = 50 * min,
        )
        assertEquals(2, summary.completed)
        assertEquals(75 * min, summary.focusedMs)
        assertEquals(1, summary.stopped)
    }

    @Test
    fun remainingMinutesRoundUp() {
        val s = LauncherSettings(focusSessionEnd = 10 * 60_000L + 1)
        assertEquals(10, dev.minimal.launcher.data.FocusSessions.remainingMinutes(s, 1))
        assertEquals(0, dev.minimal.launcher.data.FocusSessions.remainingMinutes(s, 11 * 60_000L))
    }
}
