package dev.minimal.launcher

import dev.minimal.launcher.util.CalendarEvent
import dev.minimal.launcher.util.CalendarEvents
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSelectTest {
    private val hour = 60 * 60 * 1000L
    private val now = 100 * 24 * hour + 10 * hour // 10:00 Uhr

    private val allDay = CalendarEvent(1, "Geburtstag", now - 10 * hour, now + 14 * hour, allDay = true)
    private val past = CalendarEvent(2, "Frühstück", now - 3 * hour, now - 2 * hour, allDay = false)
    private val running = CalendarEvent(3, "Meeting", now - hour / 2, now + hour / 2, allDay = false)
    private val later = CalendarEvent(4, "Zahnarzt", now + 4 * hour, now + 5 * hour, allDay = false)
    private val tomorrowAllDay = CalendarEvent(5, "Urlaub", now + 14 * hour, now + 38 * hour, allDay = true)
    private val farAway = CalendarEvent(6, "Übermorgen", now + 30 * hour, now + 31 * hour, allDay = false)

    private val all = listOf(later, tomorrowAllDay, past, allDay, farAway, running)

    @Test
    fun ordersAllDayThenTimed() {
        assertEquals(listOf(allDay, running, later), CalendarEvents.select(all, now, 3))
    }

    @Test
    fun respectsLimit() {
        assertEquals(listOf(allDay), CalendarEvents.select(all, now, 1))
    }

    @Test
    fun skipsPastAndFarEvents() {
        val result = CalendarEvents.select(listOf(past, farAway, tomorrowAllDay), now, 3)
        assertEquals(emptyList<CalendarEvent>(), result)
    }
}
