package ru.slem.taskwidget

import org.junit.Assert.fail
import org.junit.Test

class VaultScannerCancellationTest {
    @Test fun interruptedScanStopsBeforeReadingFiles() {
        var fileReads = 0
        val tree = object : VaultTree {
            override fun list(parentId: String): List<VaultEntry> {
                Thread.currentThread().interrupt()
                return listOf(VaultEntry("doc", "Note.md", false, 1, 1))
            }
            override fun read(id: String): String {
                fileReads++
                return "- [ ] Task #task"
            }
        }
        try {
            VaultScanner.scan("root", tree, "#task")
            fail("Expected interrupted scan")
        } catch (_: InterruptedException) {
            org.junit.Assert.assertEquals(0, fileReads)
        } finally {
            Thread.interrupted()
        }
    }
}