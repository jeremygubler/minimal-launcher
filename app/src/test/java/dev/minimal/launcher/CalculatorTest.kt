package dev.minimal.launcher

import dev.minimal.launcher.util.Calculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    @Test
    fun respectsOperatorPrecedence() {
        assertEquals("14", Calculator.evaluate("2+3*4"))
        assertEquals("20", Calculator.evaluate("(2+3)*4"))
        assertEquals("512", Calculator.evaluate("2^3^2"))
        assertEquals("-2", Calculator.evaluate("-4+2"))
    }

    @Test
    fun supportsGermanDecimalsAndSymbols() {
        assertEquals("2,5", Calculator.evaluate("10/4"))
        assertEquals("3,75", Calculator.evaluate("1,5 × 2,5"))
        assertEquals("4", Calculator.evaluate("8 ÷ 2"))
    }

    @Test
    fun ignoresNonCalculations() {
        assertNull(Calculator.evaluate("whatsapp"))
        assertNull(Calculator.evaluate("42"))
        assertNull(Calculator.evaluate("-5"))
        assertNull(Calculator.evaluate("2+"))
        assertNull(Calculator.evaluate("(2+3"))
        assertNull(Calculator.evaluate("1/0"))
        assertNull(Calculator.evaluate(""))
    }
}
