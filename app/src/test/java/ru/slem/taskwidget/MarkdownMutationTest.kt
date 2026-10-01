package ru.slem.taskwidget

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MarkdownMutationTest {
    private val today = LocalDate.of(2026, 10, 3)
    private fun parsed(line: String, number: Int = 1) =
        requireNotNull(TaskParser.parseLine(line, number, "#task"))

    @Test fun completesOnlyTargetLineAndPreservesCrLfAndBom() {
        val line = "- [ ] Сделать отчёт #task 📅 2026-10-05"
        val source = "\uFEFFЗаголовок\r\n$line\r\nДругая строка\r\n"
        val result = MarkdownMutation.complete(source, parsed(line, 2), today)
        val success = result as MutationResult.Success
        assertEquals("\uFEFFЗаголовок\r\n- [x] Сделать отчёт #task 📅 2026-10-05 ✅ 2026-10-03\r\nДругая строка\r\n", success.text)
        assertEquals(line, success.record.beforeLine)
    }

    @Test fun movedUniqueLineCanBeCompletedButDuplicateIsRejected() {
        val line = "- [ ] Уникальная #task"
        val moved = MarkdownMutation.complete("Заголовок\n$line\n", parsed(line, 1), today)
        assertTrue(moved is MutationResult.Success)
        val duplicate = MarkdownMutation.complete("$line\n$line\n", parsed(line), today)
        assertTrue(duplicate is MutationResult.Conflict)
        assertEquals("$line\n$line\n", (duplicate as MutationResult.Conflict).unchangedText)
    }

    @Test fun staleLineIsNeverOverwritten() {
        val line = "- [ ] Оригинал #task"
        val current = "- [ ] Отредактировано в Obsidian #task\n"
        val result = MarkdownMutation.complete(current, parsed(line), today)
        assertTrue(result is MutationResult.Conflict)
        assertEquals(current, (result as MutationResult.Conflict).unchangedText)
    }

    @Test fun inProgressTaskGoesDirectlyToDone() {
        val line = "  - [/] Задача #task"
        val result = MarkdownMutation.complete("$line\n", parsed(line), today) as MutationResult.Success
        assertEquals("  - [x] Задача #task ✅ 2026-10-03\n", result.text)
    }

    @Test fun weeklyRecurrenceCreatesOneNextTaskAndUndoRemovesIt() {
        val line = "- [ ] Еженедельно #task 🔁 every week 📅 2026-10-01"
        val source = "$line\nСледующая строка\n"
        val outcome = MarkdownMutation.complete(source, parsed(line), today)
        assertTrue("actual=$outcome", outcome is MutationResult.Success)
        val done = outcome as MutationResult.Success
        assertTrue(done.text.contains("- [x] Еженедельно #task 🔁 every week 📅 2026-10-01 ✅ 2026-10-03"))
        assertTrue(done.text.contains("- [ ] Еженедельно #task 🔁 every week 📅 2026-10-08"))
        val undoOutcome = MarkdownMutation.undo(done.text, done.record)
        assertTrue("undo=$undoOutcome text=${done.text} record=${done.record}", undoOutcome is MutationResult.Success)
        val undone = undoOutcome as MutationResult.Success
        assertEquals(source, undone.text)
    }

    @Test fun whenDoneRecurrenceUsesCompletionDate() {
        val line = "- [ ] Ежедневно #task 🔁 every day when done 📅 2026-10-01"
        val done = MarkdownMutation.complete("$line\n", parsed(line), today) as MutationResult.Success
        assertTrue(done.text.contains("📅 2026-10-04"))
    }

    @Test fun unsupportedRecurrenceDoesNotWritePartialCompletion() {
        val line = "- [ ] Сложная #task 🔁 every month on the 2nd Tuesday 📅 2026-10-01"
        val source = "$line\n"
        val result = MarkdownMutation.complete(source, parsed(line), today)
        assertTrue(result is MutationResult.Unsupported)
        assertEquals(source, (result as MutationResult.Unsupported).unchangedText)
    }

    @Test fun undoPreservesUnrelatedEditsAndRejectsChangedNextOccurrence() {
        val line = "- [ ] Дело #task 🔁 every week 📅 2026-10-01"
        val outcome = MarkdownMutation.complete("$line\nДругая\n", parsed(line), today)
        assertTrue("actual=$outcome", outcome is MutationResult.Success)
        val done = outcome as MutationResult.Success
        val externallyEdited = done.text.replace("Другая", "Изменена отдельно")
        val undoOutcome = MarkdownMutation.undo(externallyEdited, done.record)
        assertTrue("undo=$undoOutcome text=$externallyEdited record=${done.record}", undoOutcome is MutationResult.Success)
        val undone = undoOutcome as MutationResult.Success
        assertTrue(undone.text.contains("Изменена отдельно"))
        assertTrue(undone.text.contains(line))
        val modifiedNext = done.text.replace("📅 2026-10-08", "📅 2026-10-09")
        assertTrue(MarkdownMutation.undo(modifiedNext, done.record) is MutationResult.Conflict)
    }

    @Test fun recurrenceAtEndKeepsFinalNewlineAndUndoRestoresIt() {
        val line = "- [ ] Повтор #task 🔁 every week 📅 2026-10-01"
        val source = "$line\n"
        val done = MarkdownMutation.complete(source, parsed(line), today) as MutationResult.Success
        assertTrue(done.text.endsWith("📅 2026-10-08\n"))
        assertEquals(source, (MarkdownMutation.undo(done.text, done.record) as MutationResult.Success).text)
    }}