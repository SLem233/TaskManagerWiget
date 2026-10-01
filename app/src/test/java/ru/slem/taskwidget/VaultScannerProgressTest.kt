package ru.slem.taskwidget

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultScannerProgressTest {
    @Test fun reportsCurrentFileBeforeAProviderReadBlocks() {
        val readStarted = CountDownLatch(1)
        val continueRead = CountDownLatch(1)
        val observed = mutableListOf<ScanProgress>()
        val tree = object : VaultTree {
            override fun list(parentId: String) = listOf(VaultEntry("slow", "Slow.md", false, 1))
            override fun read(id: String): String {
                readStarted.countDown()
                continueRead.await(2, TimeUnit.SECONDS)
                return "- [ ] Найденная #task"
            }
        }
        val worker = Executors.newSingleThreadExecutor()
        try {
            val result = worker.submit<ScanResult> {
                VaultScanner.scan("root", tree, "#task", onProgress = { observed.add(it) })
            }
            assertTrue("The provider read should begin", readStarted.await(1, TimeUnit.SECONDS))
            assertTrue("Show which file is being read before it finishes",
                observed.any { it.currentPath == "Slow.md" && it.filesRead == 0 })
            continueRead.countDown()
            assertEquals(1, result.get(1, TimeUnit.SECONDS).tasks.size)
            assertTrue(observed.any { it.filesRead == 1 && it.tasksFound == 1 })
        } finally {
            continueRead.countDown()
            worker.shutdownNow()
        }
    }
}