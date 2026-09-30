package dev.minimal.launcher

import dev.minimal.launcher.data.IntentionEntry
import dev.minimal.launcher.data.IntentionStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentionStatsTest {
    private fun e(t: Long, pkg: String, i: String?) = IntentionEntry(t, pkg, i)

    @Test
    fun countsOpenedSkippedAndIntentions() {
        val s = IntentionStats.summarize(
            listOf(
                e(10, "insta", "Langeweile"),
                e(11, "insta", "Langeweile"),
                e(12, "insta", null),
                e(13, "mail", "Nachricht beantworten"),
                e(14, "yt", "langeweile "),
            ),
            from = 0,
        )
        assertEquals(4, s.opened)
        assertEquals(1, s.skipped)
        assertEquals(listOf("Langeweile" to 3, "Nachricht beantworten" to 1), s.byIntention)
        assertEquals(3, s.boredom)
        assertEquals("insta", s.boredomTopApp)
        assertEquals(Triple("insta", 2, 1), s.byApp.first())
    }

    @Test
    fun ignoresEntriesOutsideRange() {
        val s = IntentionStats.summarize(listOf(e(5, "a", "x"), e(50, "a", "y")), from = 10, to = 40)
        assertTrue(s.isEmpty)
    }

    @Test
    fun customTextsGroupCaseInsensitivelyKeepingFirstSpelling() {
        val s = IntentionStats.summarize(
            listOf(e(1, "a", "Wetter schauen"), e(2, "a", "wetter schauen"), e(3, "a", "Rezept")),
            from = 0,
        )
        assertEquals(listOf("Wetter schauen" to 2, "Rezept" to 1), s.byIntention)
        assertEquals(null, s.boredomTopApp)
    }
}
