package ru.slem.taskwidget

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8

object TaskDestination {
    fun normalize(input: String, vaultName: String): String? {
        val cleaned = input.trim().replace('\\', '/').trim('/')
        val parts = cleaned.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." || ':' in it }) return null
        val inside = if (parts.firstOrNull().equals(vaultName, ignoreCase = true)) parts.drop(1) else parts
        if (inside.isEmpty() || !inside.last().endsWith(".md", ignoreCase = true)) return null
        return inside.joinToString("/")
    }

    fun resolve(tree: VaultTree, rootId: String, path: String): String? {
        val parts = path.split('/')
        var parent = rootId
        for ((index, part) in parts.withIndex()) {
            val matching = tree.list(parent).filter { it.name == part && it.directory == (index < parts.lastIndex) }
            if (matching.size != 1) return null
            parent = matching.single().id
        }
        return parent
    }
}

object TaskAppender {
    fun appendText(original: String, title: String, tag: String): String? =
        appendText(original, TaskDraft(title, tag), tag)

    fun appendText(original: String, draft: TaskDraft, tag: String): String? {
        val line = draft.markdownLine(tag) ?: return null
        val ending = if (original.contains("\r\n")) "\r\n" else "\n"
        val prefix = if (original.isNotEmpty() && !original.endsWith("\n")) ending else ""
        return original + prefix + line + ending
    }
    fun append(
        documentId: String, path: String, title: String, tag: String,
        access: MarkdownDocumentAccess, journal: TaskWriteJournal
    ): WriteOutcome = append(documentId, path, TaskDraft(title, tag), tag, access, journal)

    fun append(
        documentId: String, path: String, draft: TaskDraft, tag: String,
        access: MarkdownDocumentAccess, journal: TaskWriteJournal
    ): WriteOutcome {
        val original = try { access.read(documentId) }
            catch (error: Exception) { return WriteOutcome.Failure("Чтение: ${error.message}") }
        val text = try {
            UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(original)).toString()
        } catch (_: Exception) { return WriteOutcome.Unsupported("Файл не в UTF-8") }
        val desiredText = appendText(text, draft, tag)
            ?: return WriteOutcome.Unsupported("Введите одну строку названия задачи")
        val record = MutationRecord("Добавление задачи", "", null, 0)
        val prepared = try { journal.prepare(documentId, path, record, original) }
            catch (error: Exception) { return WriteOutcome.Failure("Журнал: ${error.message}") }
        return try {
            if (!access.read(documentId).contentEquals(original)) {
                journal.discard(prepared.id)
                WriteOutcome.Conflict("Файл изменился перед добавлением")
            } else {
                val desired = desiredText.toByteArray(UTF_8)
                access.write(documentId, desired)
                if (!access.read(documentId).contentEquals(desired)) {
                    journal.setStatus(prepared.id, JournalStatus.NEEDS_RECOVERY)
                    WriteOutcome.Failure("Запись не подтверждена; резервная копия сохранена")
                } else {
                    journal.discard(prepared.id)
                    WriteOutcome.Success(prepared.id)
                }
            }
        } catch (error: Exception) {
            runCatching { journal.setStatus(prepared.id, JournalStatus.NEEDS_RECOVERY) }
            WriteOutcome.Failure("Запись: ${error.message}; резервная копия сохранена")
        }
    }
}