package ru.slem.taskwidget

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceEngineTest {
    @Test fun weeklyRuleAdvancesDueAndKeepsOpenStatus() {
        val line = "- [ ] Еженедельно #task 🔁 every week 📅 2026-10-01"
        val task = requireNotNull(TaskParser.parseLine(line, 1, "#task"))
        assertEquals("every week", task.recurrence)
        assertEquals("- [ ] Еженедельно #task 🔁 every week 📅 2026-10-08",
            RecurrenceEngine.nextLine(task, LocalDate.of(2026, 10, 3)))
    }
}