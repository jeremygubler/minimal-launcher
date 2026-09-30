package dev.minimal.launcher

import dev.minimal.launcher.data.ScreenTimeMath
import dev.minimal.launcher.data.UsageSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ScreenTimeMathTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private val mon = LocalDate.of(2026, 9, 28)
    private val tue = mon.plusDays(1)
    private fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private val min = 60_000L

    @Test
    fun sumsPerDayAndPackage() {
        val sessions = listOf(
            UsageSession("insta", at(mon, 10), at(mon, 10, 30)),
            UsageSession("insta", at(mon, 12), at(mon, 12, 10)),
            UsageSession("mail", at(tue, 9), at(tue, 9, 5)),
        )
        val result = ScreenTimeMath.perDay(sessions, zone, listOf(mon, tue))
        assertEquals(40 * min, result[mon]!!["insta"])
        assertEquals(5 * min, result[tue]!!["mail"])
        assertEquals(null, result[tue]!!["insta"])
    }

    @Test
    fun splitsSessionsAcrossMidnight() {
        val sessions = listOf(UsageSession("video", at(mon, 23, 30), at(tue, 0, 45)))
        val result = ScreenTimeMath.perDay(sessions, zone, listOf(mon, tue))
        assertEquals(30 * min, result[mon]!!["video"])
        assertEquals(45 * min, result[tue]!!["video"])
    }

    @Test
    fun ignoresDaysOutsideRange() {
        val sessions = listOf(UsageSession("x", at(mon, 1), at(mon, 2)))
        val result = ScreenTimeMath.perDay(sessions, zone, listOf(tue))
        assertEquals(emptyMap<String, Long>(), result[tue])
    }

    @Test
    fun streakCountsBackwardsFromToday() {
        val goal = 100L
        assertEquals(3, ScreenTimeMath.streak(listOf(200, 50, 80, 90), goal))
        // Heute schon drüber: Serie zählt ab gestern.
        assertEquals(2, ScreenTimeMath.streak(listOf(200, 50, 80, 150), goal))
        assertEquals(0, ScreenTimeMath.streak(listOf(50, 150, 150), goal))
        assertEquals(0, ScreenTimeMath.streak(listOf(10, 10), 0))
    }
}
