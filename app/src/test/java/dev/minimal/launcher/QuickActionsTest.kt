package dev.minimal.launcher

import dev.minimal.launcher.util.QuickAction
import dev.minimal.launcher.util.QuickActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionsTest {
    private inline fun <reified T : QuickAction> first(q: String): T? =
        QuickActions.parse(q).filterIsInstance<T>().firstOrNull()

    @Test
    fun timers() {
        assertEquals(300, first<QuickAction.Timer>("timer 5 min")?.seconds)
        assertEquals(300, first<QuickAction.Timer>("Timer 5")?.seconds)
        assertEquals(10, first<QuickAction.Timer>("10 s timer")?.seconds)
        assertEquals(7200, first<QuickAction.Timer>("timer 2 h")?.seconds)
    }

    @Test
    fun alarms() {
        assertEquals(QuickAction.Alarm(7, 30), first<QuickAction.Alarm>("wecker 7:30"))
        assertEquals(QuickAction.Alarm(6, 0), first<QuickAction.Alarm>("Wecker 6 Uhr"))
        assertEquals(null, first<QuickAction.Alarm>("wecker 25"))
    }

    @Test
    fun conversions() {
        assertEquals("10 km = 6,2137 mi", first<QuickAction.Conversion>("10 km in mi")?.text)
        assertEquals("25 °c = 77 °f", first<QuickAction.Conversion>("25 °c")?.text)
        assertEquals("273,15", first<QuickAction.Conversion>("0 c in k")?.value)
        assertEquals("0,90718", first<QuickAction.Conversion>("2 lb")?.value)
        assertTrue(QuickActions.conversions("10 km in kg").isEmpty())
    }

    @Test
    fun urlsAndPhones() {
        assertEquals("https://example.com", first<QuickAction.OpenUrl>("example.com")?.url)
        assertEquals(null, first<QuickAction.OpenUrl>("3.14"))
        assertEquals("0791234567", first<QuickAction.Call>("079 123 45 67")?.number)
        assertEquals("+41791234567", first<QuickAction.Sms>("+41 79 123 45 67")?.number)
        assertEquals(null, first<QuickAction.Call>("12345"))
    }

    @Test
    fun systemSettings() {
        assertEquals("WLAN-Einstellungen", first<QuickAction.SystemSetting>("wlan")?.label)
        assertEquals("Display", first<QuickAction.SystemSetting>("hellig")?.label)
        assertTrue(QuickActions.parse("wa").none { it is QuickAction.SystemSetting })
    }
}
