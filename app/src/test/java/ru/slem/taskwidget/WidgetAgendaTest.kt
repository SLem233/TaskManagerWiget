package ru.slem.taskwidget

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WidgetAgendaTest {
    private val today = LocalDate.of(2026, 10, 3)

    private fun task(line: String, path: String = "Folder/Note.md", node: String = "node-1") =
        IndexedTask(path, node, 1, requireNotNull(TaskParser.parseLine(line, 1, "#task")))

    @Test fun buildsRealRowsAndFindsExactIndexedTask() {
        val source = task("- [ ] Подготовить отчёт #task #project/AINav 📅 2026-10-04")
        val rows = WidgetAgenda.build(listOf(source), today, 14)
        assertEquals("Завтра", (rows[0] as WidgetRow.Header).label)
        val row = rows[1] as WidgetRow.Task
        assertEquals("Подготовить отчёт", row.title)
        assertTrue(row.detail.contains("AINav"))
        assertEquals(source, WidgetAgenda.find(listOf(source), row.id))
    }

    @Test fun taskTitleColorFollowsStartScheduledDueAndOverduePriority() {
        val cases = listOf(
            "- [ ] Будущая #task 🛫 2026-10-04 ⏳ 2026-10-05 📅 2026-10-06" to 0xFFFFFFFF.toInt(),
            "- [ ] Старт #task 🛫 2026-10-03 ⏳ 2026-10-05 📅 2026-10-06" to 0xFF0080FF.toInt(),
            "- [ ] Пора #task 🛫 2026-10-01 ⏳ 2026-10-03 📅 2026-10-06" to 0xFFFFFF80.toInt(),
            "- [ ] Дедлайн #task 🛫 2026-10-01 ⏳ 2026-10-02 📅 2026-10-03" to 0xFFFF8040.toInt(),
            "- [ ] Просрочено #task 📅 2026-10-02" to 0xFFFF0000.toInt(),
            "- [ ] Без даты #task" to 0xFFFFFFFF.toInt(),
            "- [ ] Дедлайн важнее #task 🛫 2026-10-05 ⏳ 2026-10-06 📅 2026-10-03" to
                0xFFFF8040.toInt()
        )
        cases.forEach { (line, expected) ->
            val row = WidgetAgenda.build(listOf(task(line)), today, 14)
                .filterIsInstance<WidgetRow.Task>().single()
            assertEquals(line, expected, row.titleColor)
        }
    }

    @Test fun ignoresCompletedAndRejectsUnknownOrAmbiguousIdentity() {
        val open = task("- [ ] Задача #task")
        val done = task("- [x] Готово #task")
        val rows = WidgetAgenda.build(listOf(open, done), today, 14)
        assertEquals(2, rows.size)
        val id = (rows[1] as WidgetRow.Task).id
        assertNull(WidgetAgenda.find(listOf(done), id))
        assertNull(WidgetAgenda.find(listOf(open, open), id))
        assertNull(WidgetAgenda.find(listOf(open), "forged"))
    }
}