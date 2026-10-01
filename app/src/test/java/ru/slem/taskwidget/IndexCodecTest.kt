package ru.slem.taskwidget

import org.junit.Assert.*
import org.junit.Test

class IndexCodecTest {
    @Test fun savedSnapshotReusesTasksAfterReload() {
        var opens = 0
        val tree = object : VaultTree {
            override fun list(parentId: String) = listOf(VaultEntry("a", "Note.md", false, 123, 42))
            override fun read(id: String): String {
                opens++
                return "- [ ] Найденная #task 📅 2026-10-03"
            }
        }
        val first = VaultScanner.scan("root", tree, "#task", vaultKey = "vault://one")
        val reloaded = requireNotNull(IndexCodec.decode(IndexCodec.encode(first.snapshot)))
        val next = VaultScanner.scan("root", tree, "#task", previous = reloaded, vaultKey = "vault://one")
        assertEquals(1, opens)
        assertEquals(1, next.filesReused)
        assertEquals("Найденная", next.tasks.single().task.description)
        assertEquals("Note.md", next.tasks.single().path)
    }

    @Test fun corruptOrUnknownFormatFallsBackToFullScan() {
        assertNull(IndexCodec.decode(byteArrayOf(0, 1, 2)))
        val snapshot = IndexSnapshot("vault", "#task", emptyMap())
        val bytes = IndexCodec.encode(snapshot)
        bytes[7] = 99
        assertNull(IndexCodec.decode(bytes))
    }

    @Test fun largeMarkdownLineCanBeStoredWithoutUtfShortLimit() {
        val line = "- [ ] " + "а".repeat(70_000) + " #task"
        val task = requireNotNull(TaskParser.parseLine(line, 1, "#task"))
        val snapshot = IndexSnapshot("vault", "#task", mapOf("a" to CachedFile("a", "A.md", 1, 1, listOf(task))))
        val restored = requireNotNull(IndexCodec.decode(IndexCodec.encode(snapshot)))
        assertEquals(line, restored.files.getValue("a").tasks.single().originalLine)
    }
}