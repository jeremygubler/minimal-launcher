package dev.minimal.launcher

import dev.minimal.launcher.data.UsageStore
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageDecayTest {
    private val week = 7L * 24 * 60 * 60 * 1000

    @Test
    fun halvesEveryWeek() {
        assertEquals(1.0, UsageStore.decay(0), 1e-9)
        assertEquals(0.5, UsageStore.decay(week), 1e-9)
        assertEquals(0.25, UsageStore.decay(2 * week), 1e-9)
    }

    @Test
    fun futureTimestampsDoNotIncreaseScore() {
        assertEquals(1.0, UsageStore.decay(-week), 1e-9)
    }
}
