package ru.slem.taskwidget

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate

data class CleanupResult(val text: String, val removed: Int)

object CompletedTaskCleanup {
    fun prune(markdown: String, tag: String, today: LocalDate): CleanupResult {
        val eligible = TaskParser.parseDocument(markdown, tag).filter { task ->
            task.status == TaskStatus.DONE && task.done?.plusMonths(3)?.let { !it.isAfter(today) } == true
        }.map { it.lineNumber }.toSet()
        if (eligible.isEmpty()) return CleanupResult(markdown, 0)
        val lines = Regex("(?<=\\n)").split(markdown)
        return CleanupResult(lines.filterIndexed { index, _ -> index + 1 !in eligible }.joinToString(""), eligible.size)
    }

    fun clean(
        documentId: String, path: String, access: MarkdownDocumentAccess,
        journal: TaskWriteJournal, tag: String, today: LocalDate
    ): WriteOutcome? {
        val original = try { access.read(documentId) }
            catch (error: Exception) { return WriteOutcome.Failure("Чтение $path: ${error.message}") }
        val text = try {
            UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(original)).toString()
        } catch (_: Exception) { return WriteOutcome.Unsupported("Файл $path не в UTF-8") }
        val result = prune(text, tag, today)
        if (result.removed == 0) return null
        val record = MutationRecord("Очистка ${result.removed} завершённых задач", "", null, 0)
        val prepared = try { journal.prepare(documentId, path, record, original) }
            catch (error: Exception) { return WriteOutcome.Failure("Журнал очистки: ${error.message}") }
        return try {
            if (!access.read(documentId).contentEquals(original)) {
                journal.discard(prepared.id)
                WriteOutcome.Conflict("Файл $path изменился перед очисткой")
            } else {
                val desired = result.text.toByteArray(UTF_8)
                access.write(documentId, desired)
                if (!access.read(documentId).contentEquals(desired)) {
                    journal.setStatus(prepared.id, JournalStatus.NEEDS_RECOVERY)
                    WriteOutcome.Failure("Очистка $path не подтверждена; резервная копия сохранена")
                } else {
                    journal.discard(prepared.id)
                    WriteOutcome.Success(prepared.id)
                }
            }
        } catch (error: Exception) {
            runCatching { journal.setStatus(prepared.id, JournalStatus.NEEDS_RECOVERY) }
            WriteOutcome.Failure("Очистка $path: ${error.message}; резервная копия сохранена")
        }
    }
}