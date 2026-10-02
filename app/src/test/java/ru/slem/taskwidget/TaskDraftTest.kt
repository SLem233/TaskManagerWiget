package ru.slem.taskwidget

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TaskDraftTest {
    @Test fun savesDescriptionTagsAndAllThreeDatesInTasksSyntax() {
        val draft = TaskDraft(
            description = "Подготовить письмо",
            tags = "#project/demo #urgent",
            start = LocalDate.of(2026, 10, 2),
            scheduled = LocalDate.of(2026, 10, 3),
            due = LocalDate.of(2026, 10, 4)
        )
        val text = TaskAppender.appendText("# Список\n", draft, "#task")
        assertEquals("# Список\n- [ ] Подготовить письмо #task #project/demo #urgent " +
            "🛫 2026-10-02 ⏳ 2026-10-03 📅 2026-10-04\n", text)
        val parsed = requireNotNull(TaskParser.parseDocument(requireNotNull(text), "#task").singleOrNull())
        assertEquals(draft.start, parsed.start)
        assertEquals(draft.scheduled, parsed.scheduled)
        assertEquals(draft.due, parsed.due)
        assertTrue(parsed.tags.contains("urgent"))
    }

    @Test fun doesNotDuplicateTagAlreadyPresentInDescription() {
        assertEquals("- [ ] План #task #urgent",
            TaskDraft("План #task", "#task #urgent").markdownLine("#task"))
    }
    @Test fun rejectsInvalidTagAndMultilineDescriptionBeforeWriting() {
        assertNull(TaskAppender.appendText("", TaskDraft("Название", "bad tag"), "#task"))
        assertNull(TaskAppender.appendText("", TaskDraft("Две\nстроки", "#task"), "#task"))
        assertNull(TaskAppender.appendText("", TaskDraft("", "#task"), "#task"))
    }
}