package ru.slem.taskwidget

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DeadlineAlertsTest {
    private val today = LocalDate.of(2026, 10, 2)
    private fun task(line: String): IndexedTask = IndexedTask("Tasks.md", "doc", 1,
        requireNotNull(TaskParser.parseLine(line, 1, "#task")))

    @Test fun dueTodayIsOrangeAndOverdueIsRedRegardlessOfEarlierDates() {
        val todayOnly = DeadlineAlerts.summarize(listOf(task(
            "- [ ] Сегодня #task 🛫 2026-09-20 ⏳ 2026-09-25 📅 2026-10-02")), today)
        assertEquals(DeadlineSeverity.TODAY, todayOnly.severity)
        assertEquals(0xFFFF8040.toInt(), todayOnly.color)
        val mixed = DeadlineAlerts.summarize(listOf(
            task("- [ ] Сегодня #task 📅 2026-10-02"),
            task("- [ ] Просрочено #task 📅 2026-10-01")
        ), today)
        assertEquals(DeadlineSeverity.OVERDUE, mixed.severity)
        assertEquals(0xFFFF0000.toInt(), mixed.color)
        assertEquals(1, mixed.dueToday)
        assertEquals(1, mixed.overdue)
    }

    @Test fun completedCancelledAndFutureTasksDoNotTriggerBell() {
        val summary = DeadlineAlerts.summarize(listOf(
            task("- [x] Готово #task 📅 2026-09-01 ✅ 2026-09-01"),
            task("- [-] Отмена #task 📅 2026-09-01"),
            task("- [ ] Потом #task 📅 2026-10-03")
        ), today)
        assertEquals(DeadlineSeverity.NONE, summary.severity)
    }
}