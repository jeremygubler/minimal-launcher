package dev.minimal.launcher

import dev.minimal.launcher.data.Declutter
import dev.minimal.launcher.data.Declutter.DAY_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class DeclutterTest {
    private data class A(val pkg: String, val installed: Long = 0L, val launched: Long? = null)

    private val now = 1_000 * DAY_MS

    private fun run(apps: List<A>, system: Map<String, Long> = emptyMap(), days: Int = 30) =
        Declutter.select(apps, { it.pkg }, { it.installed }, { it.launched }, system, now, days)
            .map { it.first.pkg to it.second }

    @Test
    fun suggestsAppsUnusedLongerThanPeriod() {
        val result = run(
            listOf(A("old", launched = now - 40 * DAY_MS), A("recent", launched = now - 5 * DAY_MS)),
        )
        assertEquals(listOf("old" to now - 40 * DAY_MS), result)
    }

    @Test
    fun systemUsageCountsToo() {
        // Über den Launcher lange nicht gestartet, aber per Benachrichtigung vor 2 Tagen geöffnet.
        val result = run(listOf(A("chat", launched = now - 90 * DAY_MS)), system = mapOf("chat" to now - 2 * DAY_MS))
        assertEquals(emptyList<Pair<String, Long?>>(), result)
    }

    @Test
    fun skipsFreshInstalls() {
        val result = run(listOf(A("new", installed = now - 3 * DAY_MS), A("never")))
        assertEquals(listOf("never" to null), result)
    }

    @Test
    fun neverUsedFirstThenOldest() {
        val result = run(
            listOf(A("b", launched = now - 60 * DAY_MS), A("a", launched = now - 200 * DAY_MS), A("n")),
        )
        assertEquals(listOf("n", "a", "b"), result.map { it.first })
    }

    @Test
    fun longerPeriodSuggestsFewer() {
        val apps = listOf(A("x", launched = now - 45 * DAY_MS))
        assertEquals(1, run(apps, days = 30).size)
        assertEquals(0, run(apps, days = 60).size)
    }
}
