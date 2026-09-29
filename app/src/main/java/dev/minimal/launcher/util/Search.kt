package dev.minimal.launcher.util

import dev.minimal.launcher.data.AppInfo
import java.math.BigDecimal
import java.math.MathContext
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

object AppSearch {
    private fun normalize(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Kleinere Werte = bessere Treffer, null = kein Treffer. */
    private fun score(label: String, query: String): Int? {
        val l = normalize(label)
        val q = normalize(query.trim())
        if (q.isEmpty()) return null
        val words = l.split(' ', '-', '.', '_', ':').filter { it.isNotEmpty() }
        val initials = words.joinToString("") { it.take(1) }
        return when {
            l == q -> 0
            l.startsWith(q) -> 1
            words.any { it.startsWith(q) } -> 2
            initials.startsWith(q.replace(" ", "")) -> 3
            l.contains(q) -> 4
            isSubsequence(q.replace(" ", ""), l) -> 5
            else -> null
        }
    }

    private fun isSubsequence(q: String, s: String): Boolean {
        var i = 0
        for (c in s) if (i < q.length && c == q[i]) i++
        return i == q.length
    }

    fun search(apps: List<AppInfo>, query: String): List<AppInfo> =
        apps.mapNotNull { app ->
            val s = listOfNotNull(score(app.label, query), score(app.originalLabel, query)).minOrNull()
            s?.let { app to it }
        }.sortedWith(compareBy({ it.second }, { it.first.label.length })).map { it.first }
}

object Calculator {
    fun evaluate(input: String): String? {
        val expr = input.replace(',', '.').replace('×', '*').replace('÷', '/').replace(" ", "")
        if (expr.isEmpty() || expr.none { it.isDigit() }) return null
        if (expr.drop(1).none { it in "+-*/^%" }) return null
        if (!expr.all { it.isDigit() || it in "+-*/^%.()" }) return null
        return try {
            val parser = Parser(expr)
            val value = parser.parseExpression()
            if (parser.pos != expr.length || value.isNaN() || value.isInfinite()) null else format(value)
        } catch (e: Exception) {
            null
        }
    }

    private fun format(v: Double): String =
        if (v == floor(v) && abs(v) < 1e15) {
            v.toLong().toString()
        } else {
            BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString().replace('.', ',')
        }

    private class Parser(val s: String) {
        var pos = 0

        fun parseExpression(): Double {
            var v = parseTerm()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; v += parseTerm() }
                    '-' -> { pos++; v -= parseTerm() }
                    else -> return v
                }
            }
            return v
        }

        fun parseTerm(): Double {
            var v = parseFactor()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; v *= parseFactor() }
                    '/' -> { pos++; v /= parseFactor() }
                    '%' -> { pos++; v %= parseFactor() }
                    else -> return v
                }
            }
            return v
        }

        fun parseFactor(): Double {
            val base = parseUnary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                return base.pow(parseFactor())
            }
            return base
        }

        fun parseUnary(): Double {
            if (pos < s.length && s[pos] == '-') { pos++; return -parseUnary() }
            if (pos < s.length && s[pos] == '+') { pos++; return parseUnary() }
            return parsePrimary()
        }

        fun parsePrimary(): Double {
            require(pos < s.length)
            if (s[pos] == '(') {
                pos++
                val v = parseExpression()
                require(pos < s.length && s[pos] == ')')
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(pos > start)
            return s.substring(start, pos).toDouble()
        }
    }
}
