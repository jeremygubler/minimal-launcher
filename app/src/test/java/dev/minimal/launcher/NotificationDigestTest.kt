package dev.minimal.launcher

import android.app.Notification
import dev.minimal.launcher.data.HeldNotification
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.NotificationDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationDigestTest {
    private val s = LauncherSettings(
        digestEnabled = true,
        focusApps = setOf("com.instagram.android/com.instagram.MainActivity#0"),
    )

    @Test
    fun holdsOnlyDistractingClearableNotifications() {
        assertTrue(NotificationDigest.shouldHold(s, true, "com.instagram.android", null, ongoing = false, clearable = true))
        assertFalse(NotificationDigest.shouldHold(s, true, "com.whatsapp", null, ongoing = false, clearable = true))
        assertFalse(NotificationDigest.shouldHold(s, true, "com.instagram.android", null, ongoing = true, clearable = true))
        assertFalse(NotificationDigest.shouldHold(s, true, "com.instagram.android", Notification.CATEGORY_CALL, ongoing = false, clearable = true))
        assertFalse(NotificationDigest.shouldHold(s, false, "com.instagram.android", null, ongoing = false, clearable = true))
        assertFalse(NotificationDigest.shouldHold(s.copy(digestEnabled = false), true, "com.instagram.android", null, ongoing = false, clearable = true))
    }

    @Test
    fun nextTriggerPicksEarliestUpcoming() {
        val zone = ZoneId.of("Europe/Zurich")
        val times = listOf(12 * 60, 18 * 60)
        val morning = ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 1, 12, 0, 0, 0, zone), NotificationDigest.nextTrigger(morning, times))
        val night = ZonedDateTime.of(2026, 10, 1, 19, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 2, 12, 0, 0, 0, zone), NotificationDigest.nextTrigger(night, times))
        assertEquals(null, NotificationDigest.nextTrigger(night, emptyList()))
    }

    @Test
    fun summaryGroupsByAppMostFirst() {
        val held = listOf(
            HeldNotification("1", "insta", "Anna", "hat dein Foto geliked", 1),
            HeldNotification("2", "insta", "Ben", "folgt dir jetzt", 3),
            HeldNotification("3", "tiktok", "", "Neue Videos für dich", 2),
        )
        val (title, lines) = NotificationDigest.summary(held) { if (it == "insta") "Instagram" else "TikTok" }
        assertEquals("3 Benachrichtigungen", title)
        assertEquals(listOf("Instagram (2): Ben – folgt dir jetzt", "TikTok (1): Neue Videos für dich"), lines)
    }

    @Test
    fun describesTimes() {
        assertEquals("12:00 · 18:00", NotificationDigest.describe(listOf(18 * 60, 12 * 60)))
    }
}
