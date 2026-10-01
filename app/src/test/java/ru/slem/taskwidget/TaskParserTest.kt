package ru.slem.taskwidget

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TaskParserTest {
    @Test fun parsesAcceptanceExample() {
        val line = "- [ ] Обсудить с командой #task #project/DemoProject 🛫 2026-10-01 ⏳ 2026-10-10 📅 2026-10-25"
        val task = requireNotNull(TaskParser.parseLine(line, 7, "#task"))
        assertEquals(TaskStatus.OPEN, task.status)
        assertEquals("Обсудить с командой", task.description)
        assertEquals("DemoProject", task.project)
        assertEquals(LocalDate.of(2026, 10, 1), task.start)
        assertEquals(LocalDate.of(2026, 10, 10), task.scheduled)
        assertEquals(LocalDate.of(2026, 10, 25), task.due)
        assertEquals(7, task.lineNumber)
        assertEquals(line, task.originalLine)
    }

    @Test fun ignoresOrdinaryChecklistAndNearMissTag() {
        assertNull(TaskParser.parseLine("- [ ] Проверить формат имени файла", 1, "#task"))
        assertNull(TaskParser.parseLine("- [ ] Другое #tasks", 1, "#task"))
        assertNull(TaskParser.parseLine("- [ ] Другое #task/subtag", 1, "#task"))
        assertNotNull(TaskParser.parseLine("- [ ] Другое #TASK", 1, "#task"))
    }

    @Test fun recognizesDefaultStatuses() {
        val rows = listOf(" " to TaskStatus.OPEN, "/" to TaskStatus.IN_PROGRESS,
            "x" to TaskStatus.DONE, "X" to TaskStatus.DONE, "-" to TaskStatus.CANCELLED)
        for ((symbol, status) in rows) {
            val task = requireNotNull(TaskParser.parseLine("- [$symbol] Дело #task", 1, "#task"))
            assertEquals(status, task.status)
        }
        assertNull(TaskParser.parseLine("- [?] Дело #task", 1, "#task"))
    }

    @Test fun usesTasksDefaultPrioritySymbols() {
        val cases = listOf("🔺" to TaskPriority.HIGHEST, "⏫" to TaskPriority.HIGH,
            "🔼" to TaskPriority.MEDIUM, "🔽" to TaskPriority.LOW, "⏬" to TaskPriority.LOWEST)
        for ((symbol, expected) in cases) {
            val task = requireNotNull(TaskParser.parseLine("- [ ] Дело #task $symbol", 1, "#task"))
            assertEquals(expected, task.priority)
            assertEquals("Дело", task.description)
        }
        assertEquals(TaskPriority.NORMAL, TaskParser.parseLine("- [ ] Дело #task", 1, "#task")?.priority)
    }

    @Test fun extractsRecurrenceTagsAndCompletionDate() {
        val task = requireNotNull(TaskParser.parseLine(
            "- [x] Отчёт #task #waiting 🔁 every week 📅 2026-10-01 ✅ 2026-10-02", 1, "#task"))
        assertEquals("Отчёт", task.description)
        assertEquals("every week", task.recurrence)
        assertEquals(LocalDate.of(2026, 10, 2), task.done)
        assertTrue("waiting" in task.tags)
    }

    @Test fun skipsFencedCodeButKeepsNestedListTasks() {
        val document = """```md
- [ ] Пример #task
```
- [ ] Внешняя #task
  - [/] Вложенная #task
~~~
- [ ] Второй пример #task
~~~"""
        val tasks = TaskParser.parseDocument(document, "#task")
        assertEquals(listOf("Внешняя", "Вложенная"), tasks.map { it.description })
        assertEquals(listOf(4, 5), tasks.map { it.lineNumber })
    }

    @Test fun configurableTagIsExactAndInvalidDateDoesNotCrashScan() {
        val task = requireNotNull(TaskParser.parseLine("- [ ] Дело #work 📅 2026-99-99", 1, "#work"))
        assertEquals("Дело", task.description)
        assertNull(task.due)
        assertNull(TaskParser.parseLine("- [ ] Дело #task", 1, "#work"))
    }
}