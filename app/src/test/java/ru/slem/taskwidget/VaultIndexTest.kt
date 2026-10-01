package ru.slem.taskwidget

import org.junit.Assert.*
import org.junit.Test

class VaultIndexTest {
    private class FakeTree : VaultTree {
        var entries = listOf(VaultEntry("a", "A.md", false, 10, 100), VaultEntry("b", "B.md", false, 11, 200))
        val content = mutableMapOf("a" to "- [ ] Первая #task", "b" to "- [ ] Вторая #task")
        val opened = mutableListOf<String>()
        var failRead: String? = null
        override fun list(parentId: String) = entries
        override fun read(id: String): String {
            opened += id
            if (id == failRead) error("read failed")
            return content.getValue(id)
        }
    }

    @Test fun unchangedFilesReuseParsedTasksWithoutOpeningMarkdown() {
        val tree = FakeTree()
        val first = VaultScanner.scan("root", tree, "#task", vaultKey = "vault-1")
        assertEquals(2, first.filesParsed)
        tree.opened.clear()
        val second = VaultScanner.scan("root", tree, "#task", previous = first.snapshot, vaultKey = "vault-1")
        assertEquals(emptyList<String>(), tree.opened)
        assertEquals(0, second.filesParsed)
        assertEquals(2, second.filesReused)
        assertEquals(first.tasks.map { it.task.description }, second.tasks.map { it.task.description })
    }

    @Test fun changedNewAndDeletedFilesUpdateSnapshot() {
        val tree = FakeTree()
        val first = VaultScanner.scan("root", tree, "#task", vaultKey = "vault-1")
        tree.opened.clear()
        tree.entries = listOf(VaultEntry("a", "A.md", false, 12, 101), VaultEntry("c", "C.md", false, 13, 50))
        tree.content["a"] = "- [ ] Изменена #task"
        tree.content["c"] = "- [ ] Новая #task"
        val next = VaultScanner.scan("root", tree, "#task", previous = first.snapshot, vaultKey = "vault-1")
        assertEquals(listOf("a", "c"), tree.opened)
        assertEquals(listOf("Изменена", "Новая"), next.tasks.map { it.task.description })
        assertEquals(2, next.filesParsed)
        assertEquals(1, next.filesRemoved)
        assertEquals(setOf("a", "c"), next.snapshot.files.keys)
    }

    @Test fun unknownModificationTimeIsNeverTrustedForReuse() {
        val tree = FakeTree()
        tree.entries = listOf(VaultEntry("a", "A.md", false, 0, -1))
        val first = VaultScanner.scan("root", tree, "#task", vaultKey = "vault-1")
        tree.opened.clear()
        VaultScanner.scan("root", tree, "#task", previous = first.snapshot, vaultKey = "vault-1")
        assertEquals(listOf("a"), tree.opened)
    }

    @Test fun failedReadKeepsPreviouslyIndexedTask() {
        val tree = FakeTree()
        val first = VaultScanner.scan("root", tree, "#task", vaultKey = "vault-1")
        tree.entries = listOf(VaultEntry("a", "A.md", false, 20, 100))
        tree.failRead = "a"
        val next = VaultScanner.scan("root", tree, "#task", previous = first.snapshot, vaultKey = "vault-1")
        assertEquals(listOf("Первая"), next.tasks.map { it.task.description })
        assertEquals(1, next.errors.size)
        assertEquals(1, next.filesRemoved)
    }

    @Test fun changedFilterOrVaultInvalidatesCachedParsing() {
        val tree = FakeTree()
        val first = VaultScanner.scan("root", tree, "#task", vaultKey = "vault-1")
        tree.opened.clear()
        val changedTag = VaultScanner.scan("root", tree, "#work", previous = first.snapshot, vaultKey = "vault-1")
        assertEquals(2, tree.opened.size)
        assertTrue(changedTag.tasks.isEmpty())
        tree.opened.clear()
        VaultScanner.scan("root", tree, "#task", previous = first.snapshot, vaultKey = "vault-2")
        assertEquals(2, tree.opened.size)
    }

    @Test fun changedSizeReparsesEvenWhenModificationTimeIsUnchanged() {
        val tree = FakeTree()
        tree.entries = listOf(VaultEntry("a", "A.md", false, 10, 100))
        val first = VaultScanner.scan("root", tree, "#task", vaultKey = "vault-1")
        tree.opened.clear()
        tree.entries = listOf(VaultEntry("a", "A.md", false, 10, 101))
        tree.content["a"] = "- [ ] Обновлена #task"
        val next = VaultScanner.scan("root", tree, "#task", previous = first.snapshot, vaultKey = "vault-1")
        assertEquals(listOf("a"), tree.opened)
        assertEquals("Обновлена", next.tasks.single().task.description)
    }

    @Test fun failedDirectoryListingKeepsItsCachedTasks() {
        var failNotes = false
        val tree = object : VaultTree {
            override fun list(parentId: String): List<VaultEntry> = when (parentId) {
                "root" -> listOf(VaultEntry("notes", "Notes", true, 1))
                "notes" -> if (failNotes) error("listing failed")
                    else listOf(VaultEntry("a", "A.md", false, 10, 100))
                else -> emptyList()
            }
            override fun read(id: String) = "- [ ] Важная #task"
        }
        val first = VaultScanner.scan("root", tree, "#task", vaultKey = "vault-1")
        failNotes = true
        val next = VaultScanner.scan("root", tree, "#task", previous = first.snapshot, vaultKey = "vault-1")
        assertEquals(listOf("Важная"), next.tasks.map { it.task.description })
        assertEquals(0, next.filesRemoved)
        assertEquals(1, next.errors.size)
    }}