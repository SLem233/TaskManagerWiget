package ru.slem.taskwidget

data class VaultEntry(
    val id: String, val name: String, val directory: Boolean,
    val lastModified: Long, val size: Long = -1
)

interface VaultTree {
    fun list(parentId: String): List<VaultEntry>
    fun read(id: String): String
}

data class IndexedTask(val path: String, val nodeId: String, val lastModified: Long, val task: ParsedTask)
data class CachedFile(
    val id: String, val path: String, val lastModified: Long,
    val size: Long, val tasks: List<ParsedTask>
)
data class IndexSnapshot(
    val vaultKey: String, val requiredTag: String,
    val files: Map<String, CachedFile>
)
data class ScanResult(
    val filesScanned: Int, val tasks: List<IndexedTask>, val errors: List<String>,
    val snapshot: IndexSnapshot, val filesParsed: Int,
    val filesReused: Int, val filesRemoved: Int
)
data class ScanProgress(
    val foldersRead: Int, val filesRead: Int, val tasksFound: Int,
    val currentPath: String?, val filesReused: Int = 0
)

object VaultScanner {
    fun scan(
        rootId: String,
        tree: VaultTree,
        requiredTag: String,
        excludedNames: Set<String> = setOf(".obsidian", ".trash"),
        onProgress: (ScanProgress) -> Unit = {},
        previous: IndexSnapshot? = null,
        vaultKey: String = ""
    ): ScanResult {
        val oldFiles = previous?.takeIf {
            it.vaultKey == vaultKey && it.requiredTag.equals(requiredTag, ignoreCase = true)
        }?.files.orEmpty()
        val nextFiles = linkedMapOf<String, CachedFile>()
        val tasks = mutableListOf<IndexedTask>()
        val errors = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var filesSeen = 0
        var filesRead = 0
        var filesParsed = 0
        var filesReused = 0
        var foldersRead = 0
        fun report(path: String?) = onProgress(
            ScanProgress(foldersRead, filesRead, tasks.size, path, filesReused)
        )
        fun retain(file: CachedFile, path: String = file.path) {
            val copy = file.copy(path = path)
            nextFiles[copy.id] = copy
            copy.tasks.forEach { tasks.add(IndexedTask(path, copy.id, copy.lastModified, it)) }
        }
        fun checkCancelled() {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Сканирование остановлено")
        }
        report(null)
        fun walk(parentId: String, prefix: String) {
            checkCancelled()
            if (!visited.add(parentId)) return
            val entries = try { tree.list(parentId) }
                catch (cancelled: InterruptedException) { throw cancelled }
                catch (error: Exception) {
                    errors.add("Не удалось прочитать папку $prefix: ${error.message}")
                    val prefixWithSlash = if (prefix.isEmpty()) "" else "$prefix/"
                    oldFiles.values.filter { it.path.startsWith(prefixWithSlash) }
                        .forEach { retain(it) }
                    report(prefix)
                    return
                }
            checkCancelled()
            foldersRead++
            report(prefix)
            for (entry in entries) {
                checkCancelled()
                val path = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                if (entry.directory) {
                    if (excludedNames.none { it.equals(entry.name, ignoreCase = true) }) walk(entry.id, path)
                } else if (entry.name.endsWith(".md", ignoreCase = true)) {
                    filesSeen++
                    val cached = oldFiles[entry.id]
                    val unchanged = cached != null && entry.lastModified > 0 && entry.size >= 0 &&
                        cached.lastModified == entry.lastModified && cached.size == entry.size
                    if (unchanged) {
                        retain(requireNotNull(cached), path)
                        filesReused++
                        report(path)
                        continue
                    }
                    report(path)
                    try {
                        val parsed = TaskParser.parseDocument(tree.read(entry.id), requiredTag)
                        retain(CachedFile(entry.id, path, entry.lastModified, entry.size, parsed))
                        filesParsed++
                    } catch (cancelled: InterruptedException) {
                        throw cancelled
                    } catch (error: Exception) {
                        errors.add("Не удалось прочитать $path: ${error.message}")
                        cached?.let { retain(it, path) }
                    } finally {
                        filesRead++
                        report(path)
                    }
                }
            }
        }
        walk(rootId, "")
        checkCancelled()
        return ScanResult(
            filesSeen, tasks, errors,
            IndexSnapshot(vaultKey, requiredTag, nextFiles),
            filesParsed, filesReused, oldFiles.keys.count { it !in nextFiles }
        )
    }
}