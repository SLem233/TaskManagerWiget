package ru.slem.taskwidget

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WidgetTaskActionTest {
    @Test fun usesOnlyTaskPresentInCurrentVaultIndex() {
        val line = "- [ ] Готовить отчёт #task"
        val task = IndexedTask("Note.md", "doc", 1, requireNotNull(TaskParser.parseLine(line, 1, "#task")))
        val snapshot = IndexSnapshot("vault", "#task",
            mapOf("doc" to CachedFile("doc", "Note.md", 1, 1, listOf(task.task))))
        val id = (WidgetAgenda.build(listOf(task), LocalDate.of(2026, 10, 3), 14)[1] as WidgetRow.Task).id
        val access = object : MarkdownDocumentAccess {
            var text = "$line\n"
            override fun read(id: String) = text.toByteArray()
            override fun write(id: String, bytes: ByteArray) { text = String(bytes) }
        }
        val journal = object : TaskWriteJournal {
            var entry: JournalEntry? = null
            override fun prepare(documentId: String, path: String, record: MutationRecord, before: ByteArray) =
                JournalEntry("1", documentId, path, record, JournalStatus.PREPARED).also { entry = it }
            override fun setStatus(id: String, status: JournalStatus) { entry = entry!!.copy(status = status) }
            override fun saveRecoveryBackup(id: String, before: ByteArray) = Unit
            override fun discard(id: String) { entry = null }
            override fun get(id: String) = entry
        }
        assertTrue(WidgetTaskAction.complete(snapshot, "wrong", "#task", id, access, journal,
            LocalDate.of(2026, 10, 3)) is WriteOutcome.Conflict)
        assertEquals("$line\n", access.text)
        assertTrue(WidgetTaskAction.complete(snapshot, "vault", "#another", id, access, journal,
            LocalDate.of(2026, 10, 3)) is WriteOutcome.Conflict)
        assertEquals("$line\n", access.text)
        assertTrue(WidgetTaskAction.complete(snapshot, "vault", "#task", id, access, journal,
            LocalDate.of(2026, 10, 3)) is WriteOutcome.Success)
        assertTrue(access.text.contains("[x]"))
    }
}