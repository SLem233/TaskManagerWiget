package ru.slem.taskwidget

import android.content.Context
import java.time.LocalDate

object VaultRepository {
    private val lock = Any()

    fun indexedTasks(context: Context): List<IndexedTask> {
        val snapshot = LocalTaskIndex(context).load() ?: return emptyList()
        if (snapshot.vaultKey != WidgetPreferences.vaultUri(context) ||
            snapshot.requiredTag != WidgetPreferences.tag(context)) return emptyList()
        return tasks(snapshot)
    }

    fun tasks(snapshot: IndexSnapshot): List<IndexedTask> = snapshot.files.values.flatMap { file ->
        file.tasks.map { IndexedTask(file.path, file.id, file.lastModified, it) }
    }

    fun scan(context: Context, onProgress: (ScanProgress) -> Unit = {}): ScanResult = synchronized(lock) {
        val uri = WidgetPreferences.treeUri(context) ?: error("Сначала выберите Vault")
        val key = uri.toString()
        val tree = SafVaultTree(context.contentResolver, uri)
        val index = LocalTaskIndex(context)
        val result = VaultScanner.scan(tree.rootId, tree, WidgetPreferences.tag(context),
            onProgress = onProgress, previous = index.load(), vaultKey = key)
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Сканирование остановлено")
        index.save(result.snapshot)
        WidgetPreferences.setScanError(context, result.errors.firstOrNull())
        result
    }

    fun complete(context: Context, rowId: String): WriteOutcome = synchronized(lock) {
        val uri = WidgetPreferences.treeUri(context)
            ?: return@synchronized WriteOutcome.Conflict("Сначала выберите Vault")
        val index = LocalTaskIndex(context)
        val journal = LocalTaskWriteJournal(context, uri.toString())
        val access = SafMarkdownDocumentAccess(context.contentResolver, uri)
        try {
            val snapshot = index.load()
            val task = snapshot?.let { WidgetAgenda.find(tasks(it), rowId) }
            val outcome = WidgetTaskAction.complete(snapshot, uri.toString(), WidgetPreferences.tag(context), rowId,
                access, journal, LocalDate.now())
            if (outcome is WriteOutcome.Success && task != null) {
                val siblings = snapshot.files[task.nodeId]?.tasks.orEmpty().filterNot { it == task.task }
                index.save(refreshDocument(snapshot, task.nodeId, task.path, access, siblings))
            }
            outcome
        } finally { journal.close() }
    }

    fun undo(context: Context, actionId: String): WriteOutcome = synchronized(lock) {
        val uri = WidgetPreferences.treeUri(context)
            ?: return@synchronized WriteOutcome.Conflict("Сначала выберите Vault")
        val journal = LocalTaskWriteJournal(context, uri.toString())
        try {
            val entry = journal.get(actionId)
                ?: return@synchronized WriteOutcome.Conflict("Действие не найдено")
            val access = SafMarkdownDocumentAccess(context.contentResolver, uri)
            val outcome = TaskWriter.undo(actionId, access, journal)
            if (outcome is WriteOutcome.Success) {
                val index = LocalTaskIndex(context)
                index.load()?.let { snapshot ->
                    val restored = TaskParser.parseLine(entry.record.beforeLine,
                        entry.record.originalLineNumber, snapshot.requiredTag)
                    val cached = snapshot.files[entry.documentId]?.tasks.orEmpty()
                    val fallback = cached.filterNot {
                        it.originalLine == entry.record.afterLine &&
                            it.lineNumber == entry.record.originalLineNumber
                    }.plus(listOfNotNull(restored)).distinctBy { it.lineNumber to it.originalLine }
                    index.save(refreshDocument(snapshot, entry.documentId, entry.path, access, fallback))
                }
            }
            outcome
        } finally { journal.close() }
    }
    private fun refreshDocument(
        snapshot: IndexSnapshot, documentId: String, path: String,
        access: MarkdownDocumentAccess, fallback: List<ParsedTask>
    ): IndexSnapshot {
        val parsed = try {
            TaskParser.parseDocument(String(access.read(documentId), Charsets.UTF_8), snapshot.requiredTag)
        } catch (_: Exception) { fallback }
        val file = CachedFile(documentId, path, 0, -1, parsed)
        return snapshot.copy(files = snapshot.files + (documentId to file))
    }
}
