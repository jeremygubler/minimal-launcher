package dev.minimal.launcher

import dev.minimal.launcher.data.KansoStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Lesbarkeit: Text ≥ 7:1 (WCAG AAA), Akzent ≥ 4.5:1 (AA) auf dem Stil-Hintergrund. */
class KansoStyleTest {
    private fun luminance(c: Long): Double {
        fun ch(shift: Int): Double {
            val v = (c shr shift and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    private fun contrast(a: Long, b: Long): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun stylesAreReadable() {
        KansoStyle.entries.filter { it.active }.forEach { s ->
            assertTrue("${s.name} Text", contrast(s.background, s.text) >= 7.0)
            assertTrue("${s.name} Akzent", contrast(s.background, s.accent) >= 4.5)
        }
    }
}
