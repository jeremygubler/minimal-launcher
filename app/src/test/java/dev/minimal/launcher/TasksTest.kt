package dev.minimal.launcher

import dev.minimal.launcher.data.TaskItem
import dev.minimal.launcher.data.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TasksTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private val today = LocalDate.of(2026, 9, 30)
    private fun at(day: LocalDate, h: Int) = day.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun parsesCommands() {
        assertEquals("Milch kaufen" to null, Tasks.parseCommand("todo Milch kaufen", today))
        assertEquals("Zahnarzt anrufen" to today.plusDays(1), Tasks.parseCommand("Aufgabe morgen Zahnarzt anrufen", today))
        assertEquals("Steuern" to today.plusDays(2), Tasks.parseCommand("todo Steuern übermorgen", today))
        assertEquals("Wäsche" to today, Tasks.parseCommand("task heute Wäsche", today))
        assertNull(Tasks.parseCommand("todo morgen", today))
        assertNull(Tasks.parseCommand("toDoList", today))
    }

    @Test
    fun showsOnlyDueAndTodaysDone() {
        val tasks = listOf(
            TaskItem("a", "offen"),
            TaskItem("b", "morgen", due = today.plusDays(1)),
            TaskItem("c", "überfällig", due = today.minusDays(2)),
            TaskItem("d", "heute erledigt", doneAt = at(today, 9)),
            TaskItem("e", "gestern erledigt", doneAt = at(today.minusDays(1), 20)),
        )
        val visible = Tasks.visible(tasks, today, zone).map { it.id }
        assertEquals(listOf("c", "a", "d"), visible)
    }

    @Test
    fun purgesTasksDoneBeforeToday() {
        val tasks = listOf(
            TaskItem("keep", "offen"),
            TaskItem("today", "heute erledigt", doneAt = at(today, 8)),
            TaskItem("old", "alt", doneAt = at(today.minusDays(1), 23)),
        )
        assertEquals(listOf("keep", "today"), Tasks.purge(tasks, today, zone).map { it.id })
    }
}
