package ru.slem.taskwidget

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AgendaProjectorTest {
    private val today = LocalDate.of(2026, 9, 30)
    private fun task(text: String, status: String = " "): IndexedTask {
        val line = "- [$status] $text #task"
        return IndexedTask("Notes.md", text, 1, requireNotNull(TaskParser.parseLine(line, 1, "#task")))
    }

    @Test fun onlyPastDueIsOverdueAndPastScheduledIsToday() {
        val tasks = listOf(
            task("Дедлайн вчера 📅 2026-09-29"),
            task("План вчера ⏳ 2026-09-29 📅 2026-10-10"),
            task("Старт вчера 🛫 2026-09-29"),
            task("План в будущем 🛫 2026-10-01 ⏳ 2026-10-10 📅 2026-10-25"),
            task("Без даты")
        )
        val groups = AgendaProjector.project(tasks, today, horizonDays = 14)
        assertEquals(listOf(AgendaGroupKey.Overdue, AgendaGroupKey.Today,
            AgendaGroupKey.Date(LocalDate.of(2026, 10, 10)), AgendaGroupKey.Undated), groups.map { it.key })
        assertEquals(listOf("План вчера", "Старт вчера"), groups[1].tasks.map { it.task.description })
    }

    @Test fun fourteenDaysIncludesTodayThroughDayThirteen() {
        val groups = AgendaProjector.project(listOf(
            task("Внутри ⏳ 2026-10-13"), task("За пределом ⏳ 2026-10-14")), today, 14)
        assertEquals(listOf("Внутри"), groups.flatMap { it.tasks }.map { it.task.description })
    }

    @Test fun hidesDoneAndCancelledAndSortsByTasksPriorityThenDue() {
        val groups = AgendaProjector.project(listOf(
            task("Низкий ⏳ 2026-09-30 🔽"),
            task("Высокий поздний ⏳ 2026-09-30 📅 2026-10-05 ⏫"),
            task("Высокий ранний ⏳ 2026-09-30 📅 2026-10-02 ⏫"),
            task("В работе ⏳ 2026-09-30", "/"),
            task("Готово ⏳ 2026-09-30", "x"),
            task("Отменено ⏳ 2026-09-30", "-")), today, 14)
        assertEquals(listOf("Высокий ранний", "Высокий поздний", "В работе", "Низкий"),
            groups.single().tasks.map { it.task.description })
    }
}