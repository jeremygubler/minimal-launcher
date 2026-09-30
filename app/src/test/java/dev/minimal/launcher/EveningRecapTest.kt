package dev.minimal.launcher

import dev.minimal.launcher.data.EveningRecap
import dev.minimal.launcher.data.RecapInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class EveningRecapTest {
    private val min = 60_000L
    private val base = RecapInput(null, null, 0, 0, null, 0, 0, 0)

    @Test
    fun withinGoalShowsStreak() {
        val (title, text) = EveningRecap.compose(
            base.copy(todayMs = 150 * min, weekAverageMs = 200 * min, goalMs = 180 * min, streak = 4),
        )
        assertEquals("Dein Tag · 2 h 30 min Bildschirmzeit", title)
        assertTrue(text.contains("Im Ziel von 3 h 0 min – 4 Tage in Folge"))
        assertTrue(text.contains("50 min weniger als dein Schnitt"))
        assertTrue(text.endsWith("Gute Nacht."))
    }

    @Test
    fun overGoalAndSmallDifferenceIgnored() {
        val (_, text) = EveningRecap.compose(
            base.copy(todayMs = 200 * min, weekAverageMs = 198 * min, goalMs = 180 * min),
        )
        assertTrue(text.contains("20 min über deinem Ziel"))
        assertFalse(text.contains("Schnitt"))
    }

    @Test
    fun intentionsTasksAndTopApp() {
        val (_, text) = EveningRecap.compose(
            base.copy(todayMs = 60 * min, topApp = "Instagram" to 25 * min, opened = 3, skipped = 2, tasksDone = 1),
        )
        assertTrue(text.contains("Am meisten: Instagram (25 min)"))
        assertTrue(text.contains("3× bewusst geöffnet · 2× verzichtet"))
        assertTrue(text.contains("1 Aufgabe erledigt"))
        val (_, withSessions) = EveningRecap.compose(base.copy(focusSessions = 2, focusMs = 50 * min))
        assertTrue(withSessions.contains("2 Fokus-Sitzungen (50 min)"))
    }

    @Test
    fun quietDayWithoutUsageAccess() {
        assertEquals("Dein Tag" to "Ein ruhiger Tag. Gute Nacht.", EveningRecap.compose(base))
    }

    @Test
    fun nextTriggerTodayOrTomorrow() {
        val zone = ZoneId.of("Europe/Zurich")
        val afternoon = ZonedDateTime.of(2026, 9, 30, 15, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 9, 30, 21, 0, 0, 0, zone), EveningRecap.nextTrigger(afternoon, 21 * 60))
        val late = ZonedDateTime.of(2026, 9, 30, 22, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 1, 21, 0, 0, 0, zone), EveningRecap.nextTrigger(late, 21 * 60))
    }
}
