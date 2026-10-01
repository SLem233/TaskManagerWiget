package ru.slem.taskwidget

import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TaskWriterTest {
    private val line = "- [ ] Отправить письмо #task"
    private val task = IndexedTask("Note.md", "doc-id", 1,
        requireNotNull(TaskParser.parseLine(line, 1, "#task")))
    private val today = LocalDate.of(2026, 10, 3)

    private class FakeDocument(var text: String) : MarkdownDocumentAccess {
        var reads = 0
        var writes = 0
        var onSecondRead: (() -> Unit)? = null
        var writePartial = false
        override fun read(id: String): ByteArray {
            reads++
            if (reads == 2) onSecondRead?.invoke()
            return text.toByteArray(UTF_8)
        }
        override fun write(id: String, bytes: ByteArray) {
            writes++
            text = if (writePartial) "частичная запись" else String(bytes, UTF_8)
        }
    }
    private class FakeJournal : TaskWriteJournal {
        val entries = linkedMapOf<String, JournalEntry>()
        var backup: ByteArray? = null
        override fun prepare(documentId: String, path: String, record: MutationRecord, before: ByteArray): JournalEntry {
            backup = before.copyOf()
            return JournalEntry("action-1", documentId, path, record, JournalStatus.PREPARED).also { entries[it.id] = it }
        }
        override fun setStatus(id: String, status: JournalStatus) {
            entries[id] = requireNotNull(entries[id]).copy(status = status)
        }
        override fun saveRecoveryBackup(id: String, before: ByteArray) { backup = before.copyOf() }
        override fun discard(id: String) { entries.remove(id); backup = null }
        override fun get(id: String) = entries[id]
    }

    @Test fun verifiesWriteAndRecordsUndoInformation() {
        val document = FakeDocument("$line\n")
        val journal = FakeJournal()
        val result = TaskWriter.complete(task, document, journal, today)
        assertTrue(result is WriteOutcome.Success)
        assertEquals(1, document.writes)
        assertEquals("- [x] Отправить письмо #task ✅ 2026-10-03\n", document.text)
        assertEquals(JournalStatus.DONE, journal.entries.values.single().status)
        assertEquals("$line\n", String(requireNotNull(journal.backup), UTF_8))
    }

    @Test fun externalChangeBetweenReadsPreventsWrite() {
        val document = FakeDocument("$line\n")
        document.onSecondRead = { document.text = "- [ ] Изменено в Obsidian #task\n" }
        val journal = FakeJournal()
        val result = TaskWriter.complete(task, document, journal, today)
        assertTrue(result is WriteOutcome.Conflict)
        assertEquals(0, document.writes)
        assertTrue(journal.entries.isEmpty())
        assertEquals("- [ ] Изменено в Obsidian #task\n", document.text)
    }

    @Test fun failedVerificationKeepsRecoveryBackup() {
        val document = FakeDocument("$line\n")
        document.writePartial = true
        val journal = FakeJournal()
        val result = TaskWriter.complete(task, document, journal, today)
        assertTrue(result is WriteOutcome.Failure)
        assertEquals(JournalStatus.NEEDS_RECOVERY, journal.entries.values.single().status)
        assertEquals("$line\n", String(requireNotNull(journal.backup), UTF_8))
    }

    @Test fun failedUndoKeepsLatestUnrelatedEditsInRecoveryBackup() {
        val document = FakeDocument("$line\nДругая строка\n")
        val journal = FakeJournal()
        val actionId = (TaskWriter.complete(task, document, journal, today) as WriteOutcome.Success).actionId
        document.text = document.text.replace("Другая строка", "Новая правка")
        val beforeUndo = document.text
        document.writePartial = true
        document.reads = 0
        val outcome = TaskWriter.undo(actionId, document, journal)
        assertTrue(outcome is WriteOutcome.Failure)
        assertEquals(JournalStatus.NEEDS_RECOVERY, journal.entries[actionId]?.status)
        assertEquals(beforeUndo, String(requireNotNull(journal.backup), UTF_8))
    }
    @Test fun undoChangesOnlyCompletedLineAfterAnotherEdit() {
        val document = FakeDocument("$line\nДругая строка\n")
        val journal = FakeJournal()
        val completed = TaskWriter.complete(task, document, journal, today) as WriteOutcome.Success
        document.text = document.text.replace("Другая строка", "Отредактирована отдельно")
        document.reads = 0
        val undone = TaskWriter.undo(completed.actionId, document, journal)
        assertTrue(undone is WriteOutcome.Success)
        assertEquals("$line\nОтредактирована отдельно\n", document.text)
        assertEquals(JournalStatus.UNDONE, journal.entries.values.single().status)
    }
}