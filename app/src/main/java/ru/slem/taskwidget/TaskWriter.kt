package ru.slem.taskwidget

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate

interface MarkdownDocumentAccess {
    fun read(id: String): ByteArray
    fun write(id: String, bytes: ByteArray)
}

enum class JournalStatus { PREPARED, DONE, NEEDS_RECOVERY, UNDONE }
data class JournalEntry(
    val id: String,
    val documentId: String,
    val path: String,
    val record: MutationRecord,
    val status: JournalStatus,
    val createdAt: Long = 0,
    val legacy: Boolean = false
)

interface TaskWriteJournal {
    fun prepare(documentId: String, path: String, record: MutationRecord, before: ByteArray): JournalEntry
    fun setStatus(id: String, status: JournalStatus)
    fun saveRecoveryBackup(id: String, before: ByteArray)
    fun discard(id: String)
    fun get(id: String): JournalEntry?
}

sealed interface WriteOutcome {
    data class Success(val actionId: String) : WriteOutcome
    data class Conflict(val reason: String) : WriteOutcome
    data class Unsupported(val reason: String) : WriteOutcome
    data class Failure(val reason: String) : WriteOutcome
}

object TaskWriter {
    fun complete(
        task: IndexedTask, access: MarkdownDocumentAccess,
        journal: TaskWriteJournal, today: LocalDate
    ): WriteOutcome {
        val original = try { access.read(task.nodeId) }
            catch (error: Exception) { return WriteOutcome.Failure("Чтение: ${error.message}") }
        val text = decode(original) ?: return WriteOutcome.Unsupported("Файл не в UTF-8")
        val mutation = when (val result = MarkdownMutation.complete(text, task.task, today)) {
            is MutationResult.Success -> result
            is MutationResult.Conflict -> return WriteOutcome.Conflict(result.reason)
            is MutationResult.Unsupported -> return WriteOutcome.Unsupported(result.reason)
        }
        val prepared = try { journal.prepare(task.nodeId, task.path, mutation.record, original) }
            catch (error: Exception) { return WriteOutcome.Failure("Журнал: ${error.message}") }
        return try {
            if (!access.read(task.nodeId).contentEquals(original)) {
                journal.discard(prepared.id)
                WriteOutcome.Conflict("Файл изменился после чтения")
            } else {
                val desired = mutation.text.toByteArray(UTF_8)
                access.write(task.nodeId, desired)
                if (!access.read(task.nodeId).contentEquals(desired)) {
                    journal.setStatus(prepared.id, JournalStatus.NEEDS_RECOVERY)
                    WriteOutcome.Failure("Запись не подтверждена; резервная копия сохранена")
                } else {
                    journal.setStatus(prepared.id, JournalStatus.DONE)
                    WriteOutcome.Success(prepared.id)
                }
            }
        } catch (error: Exception) {
            runCatching { journal.setStatus(prepared.id, JournalStatus.NEEDS_RECOVERY) }
            WriteOutcome.Failure("Запись: ${error.message}; резервная копия сохранена")
        }
    }

    fun undo(actionId: String, access: MarkdownDocumentAccess, journal: TaskWriteJournal): WriteOutcome {
        val entry = journal.get(actionId) ?: return WriteOutcome.Conflict("Действие не найдено")
        if (entry.status != JournalStatus.DONE) return WriteOutcome.Conflict("Действие нельзя отменить")
        val current = try { access.read(entry.documentId) }
            catch (error: Exception) { return WriteOutcome.Failure("Чтение: ${error.message}") }
        val text = decode(current) ?: return WriteOutcome.Unsupported("Файл не в UTF-8")
        val mutation = when (val result = MarkdownMutation.undo(text, entry.record)) {
            is MutationResult.Success -> result
            is MutationResult.Conflict -> return WriteOutcome.Conflict(result.reason)
            is MutationResult.Unsupported -> return WriteOutcome.Unsupported(result.reason)
        }
        return try {
            if (!access.read(entry.documentId).contentEquals(current)) {
                WriteOutcome.Conflict("Файл изменился перед отменой")
            } else {
                try { journal.saveRecoveryBackup(actionId, current) }
                catch (error: Exception) {
                    return WriteOutcome.Failure("Журнал отмены: ${error.message}")
                }
                val desired = mutation.text.toByteArray(UTF_8)
                access.write(entry.documentId, desired)
                if (!access.read(entry.documentId).contentEquals(desired)) {
                    journal.setStatus(actionId, JournalStatus.NEEDS_RECOVERY)
                    WriteOutcome.Failure("Отмена не подтверждена")
                } else {
                    journal.setStatus(actionId, JournalStatus.UNDONE)
                    WriteOutcome.Success(actionId)
                }
            }
        } catch (error: Exception) {
            runCatching { journal.setStatus(actionId, JournalStatus.NEEDS_RECOVERY) }
            WriteOutcome.Failure("Отмена: ${error.message}")
        }
    }

    private fun decode(bytes: ByteArray): String? = try {
        UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) { null }
}