package dev.minimal.launcher

import dev.minimal.launcher.data.Focus
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.PageSchedule
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
}
