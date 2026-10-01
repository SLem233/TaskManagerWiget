package ru.slem.taskwidget

import org.junit.Assert.*
import org.junit.Test

class VaultScannerTest {
    private val entries = mapOf(
        "root" to listOf(VaultEntry("notes", "Notes", true, 0), VaultEntry("settings", ".obsidian", true, 0), VaultEntry("trash", ".trash", true, 0), VaultEntry("readme", "README.md", false, 1)),
        "notes" to listOf(VaultEntry("work", "Work.md", false, 2), VaultEntry("txt", "other.txt", false, 3)),
        "settings" to listOf(VaultEntry("hidden", "hidden.md", false, 4)),
        "trash" to listOf(VaultEntry("deleted", "deleted.md", false, 5))
    )
    private val texts = mapOf("work" to "- [ ] Рабочая #task", "readme" to "- [ ] Корневая #task",
        "hidden" to "- [ ] Скрытая #task", "deleted" to "- [ ] Удалённая #task")

    @Test fun scansMarkdownRecursivelyAndExcludesVaultInternals() {
        val tree = object : VaultTree {
            override fun list(parentId: String) = entries[parentId].orEmpty()
            override fun read(id: String) = texts.getValue(id)
        }
        val result = VaultScanner.scan("root", tree, "#task")
        assertEquals(2, result.filesScanned)
        assertEquals(listOf("Notes/Work.md", "README.md"), result.tasks.map { it.path })
        assertEquals(listOf("Рабочая", "Корневая"), result.tasks.map { it.task.description })
        assertTrue(result.errors.isEmpty())
    }

    @Test fun unreadableNoteIsReportedWithoutDiscardingOtherTasks() {
        val tree = object : VaultTree {
            override fun list(parentId: String) = entries[parentId].orEmpty()
            override fun read(id: String): String {
                if (id == "work") throw IllegalStateException("permission lost")
                return texts.getValue(id)
            }
        }
        val result = VaultScanner.scan("root", tree, "#task")
        assertEquals(listOf("README.md"), result.tasks.map { it.path })
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains("Work.md"))
    }
}