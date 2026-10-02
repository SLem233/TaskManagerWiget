package ru.slem.taskwidget

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CompletedTaskCleanupTest {
    @Test fun removesOnlyTaggedDoneLineAfterThreeCalendarMonths() {
        val source = "# Список\r\n- [x] Старое #task ✅ 2026-06-30\r\n" +
            "- [x] Недавнее #task ✅ 2026-07-03\r\n" +
            "- [x] Другое без тега ✅ 2026-01-01\r\n" +
            "- [ ] Активное #task 📅 2026-10-02\r\n"
        val result = CompletedTaskCleanup.prune(source, "#task", LocalDate.of(2026, 10, 2))
        assertEquals(1, result.removed)
        assertEquals(source.replace("- [x] Старое #task ✅ 2026-06-30\r\n", ""), result.text)
    }

    @Test fun leavesUnknownCompletionDateAndFencedExampleUntouched() {
        val source = "- [x] Без даты #task\n- [x] Ошибка #task ✅ 2026-99-99\n" +
            "```md\n- [x] Пример #task ✅ 2020-01-01\n```\n"
        val result = CompletedTaskCleanup.prune(source, "#task", LocalDate.of(2026, 10, 2))
        assertEquals(0, result.removed)
        assertEquals(source, result.text)
    }

    @Test fun exactThreeMonthAnniversaryIsEligible() {
        val line = "- [x] Задача #task ✅ 2026-07-02\n"
        assertEquals(1, CompletedTaskCleanup.prune(line, "#task",
            LocalDate.of(2026, 10, 2)).removed)
        assertEquals(0, CompletedTaskCleanup.prune(line, "#task",
            LocalDate.of(2026, 10, 1)).removed)
    }
}