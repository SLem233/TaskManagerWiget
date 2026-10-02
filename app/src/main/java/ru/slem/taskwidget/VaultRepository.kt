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
        val scanned = VaultScanner.scan(tree.rootId, tree, WidgetPreferences.tag(context),
            onProgress = onProgress, previous = index.load(), vaultKey = key)
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Сканирование остановлено")
        val result = cleanExpired(context, uri, scanned)
        index.save(result.snapshot)
        WidgetPreferences.setScanError(context, result.errors.firstOrNull())
        DeadlineNotifications.update(context, result.tasks)
        result
    }

    private fun cleanExpired(context: Context, uri: android.net.Uri, scanned: ScanResult): ScanResult {
        val today = LocalDate.now()
        val candidates = scanned.snapshot.files.values.filter { file ->
            file.tasks.any { it.status == TaskStatus.DONE &&
                it.done?.plusMonths(3)?.let { anniversary -> !anniversary.isAfter(today) } == true }
        }
        if (candidates.isEmpty()) return scanned
        val access = SafMarkdownDocumentAccess(context.contentResolver, uri)
        val files = scanned.snapshot.files.toMutableMap()
        val errors = scanned.errors.toMutableList()
        LocalTaskWriteJournal(context, uri.toString()).use { journal ->
            for (file in candidates) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Очистка остановлена")
                when (val outcome = CompletedTaskCleanup.clean(file.id, file.path, access,
                    journal, scanned.snapshot.requiredTag, today)) {
                    is WriteOutcome.Success -> {
                        val fresh = runCatching { String(access.read(file.id), Charsets.UTF_8) }.getOrNull()
                        if (fresh == null) errors.add("Не удалось перечитать ${file.path} после очистки")
                        else files[file.id] = file.copy(lastModified = 0, size = -1,
                            tasks = TaskParser.parseDocument(fresh, scanned.snapshot.requiredTag))
                    }
                    is WriteOutcome.Conflict -> errors.add(outcome.reason)
                    is WriteOutcome.Failure -> errors.add(outcome.reason)
                    is WriteOutcome.Unsupported -> errors.add(outcome.reason)
                    null -> Unit
                }
            }
        }
        val snapshot = scanned.snapshot.copy(files = files)
        return scanned.copy(snapshot = snapshot, tasks = tasks(snapshot), errors = errors)
    }
    fun add(context: Context, title: String): WriteOutcome =
        add(context, TaskDraft(title, WidgetPreferences.tag(context)))

    fun add(context: Context, draft: TaskDraft): WriteOutcome = synchronized(lock) {
        val uri = WidgetPreferences.treeUri(context)
            ?: return@synchronized WriteOutcome.Conflict("Сначала выберите Vault")
        val path = WidgetPreferences.addFile(context)
        val tree = SafVaultTree(context.contentResolver, uri)
        val id = try { TaskDestination.resolve(tree, tree.rootId, path) }
            catch (error: Exception) { return@synchronized WriteOutcome.Failure("Поиск файла: ${error.message}") }
            ?: return@synchronized WriteOutcome.Conflict("Файл $path не найден в Vault")
        val access = SafMarkdownDocumentAccess(context.contentResolver, uri)
        val journal = LocalTaskWriteJournal(context, uri.toString())
        try {
            val outcome = TaskAppender.append(id, path, draft, WidgetPreferences.tag(context), access, journal)
            if (outcome is WriteOutcome.Success) {
                runCatching {
                    LocalTaskIndex(context).load()?.let { snapshot ->
                        if (snapshot.vaultKey == uri.toString() && snapshot.requiredTag == WidgetPreferences.tag(context)) {
                            val parsed = TaskParser.parseDocument(String(access.read(id), Charsets.UTF_8), snapshot.requiredTag)
                            val file = CachedFile(id, path, 0, -1, parsed)
                            LocalTaskIndex(context).save(snapshot.copy(files = snapshot.files + (id to file)))
                        }
                    }
                    DeadlineNotifications.update(context, indexedTasks(context))
                }.onFailure { WidgetPreferences.setScanError(context, "Индекс требует обновления") }
            }
            outcome
        } finally { journal.close() }
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
