package ru.slem.taskwidget

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SafeNewWriteTest {
    private class Document(var text: String) : MarkdownDocumentAccess {
        var reads = 0
        var writes = 0
        var externalEdit = false
        var partialWrite = false
        override fun read(id: String): ByteArray {
            reads++
            if (reads == 2 && externalEdit) text += "внешняя правка\n"
            return text.toByteArray()
        }
        override fun write(id: String, bytes: ByteArray) {
            writes++
            text = if (partialWrite) "частично" else String(bytes)
        }
    }
    private class Journal : TaskWriteJournal {
        var status: JournalStatus? = null
        var backup: ByteArray? = null
        override fun prepare(documentId: String, path: String, record: MutationRecord,
            before: ByteArray): JournalEntry {
            status = JournalStatus.PREPARED
            backup = before.copyOf()
            return JournalEntry("id", documentId, path, record, JournalStatus.PREPARED)
        }
        override fun setStatus(id: String, status: JournalStatus) { this.status = status }
        override fun saveRecoveryBackup(id: String, before: ByteArray) { backup = before }
        override fun discard(id: String) { status = null; backup = null }
        override fun get(id: String): JournalEntry? = null
    }

    @Test fun appendConflictingFileDoesNotWrite() {
        val doc = Document("# List\n").apply { externalEdit = true }
        val journal = Journal()
        assertTrue(TaskAppender.append("doc", "List.md", "Новая", "#task", doc, journal)
            is WriteOutcome.Conflict)
        assertEquals(0, doc.writes)
        assertNull(journal.backup)
    }

    @Test fun partialAppendKeepsRecoveryBackup() {
        val doc = Document("# List\n").apply { partialWrite = true }
        val journal = Journal()
        assertTrue(TaskAppender.append("doc", "List.md", "Новая", "#task", doc, journal)
            is WriteOutcome.Failure)
        assertEquals(JournalStatus.NEEDS_RECOVERY, journal.status)
        assertEquals("# List\n", String(requireNotNull(journal.backup)))
    }

    @Test fun expiredCleanupIsVerifiedAndKeepsBackupOnPartialWrite() {
        val doc = Document("- [x] Старое #task ✅ 2026-01-01\n").apply { partialWrite = true }
        val journal = Journal()
        assertTrue(CompletedTaskCleanup.clean("doc", "List.md", doc, journal,
            "#task", LocalDate.of(2026, 10, 2)) is WriteOutcome.Failure)
        assertEquals(JournalStatus.NEEDS_RECOVERY, journal.status)
        assertEquals("- [x] Старое #task ✅ 2026-01-01\n", String(requireNotNull(journal.backup)))
    }
}