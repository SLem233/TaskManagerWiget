package ru.slem.taskwidget

import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStatusFormatterTest {
    @Test fun makesLongScanProgressVisibleToUser() {
        val text = ScanStatusFormatter.format(
            ScanProgress(foldersRead = 4, filesRead = 17, tasksFound = 2, currentPath = "Notes/Plan.md"),
            elapsedMillis = 65_000
        )
        assertTrue(text.contains("17"))
        assertTrue(text.contains("2"))
        assertTrue(text.contains("1:05"))
        assertTrue(text.contains("Plan.md"))
    }

    @Test fun countsFilesReusedFromIndexAsChecked() {
        val text = ScanStatusFormatter.format(
            ScanProgress(foldersRead = 3, filesRead = 0, tasksFound = 2,
                currentPath = "A.md", filesReused = 10), elapsedMillis = 1_000)
        assertTrue(text.contains("Проверено файлов: 10"))
        assertTrue(text.contains("из индекса: 10"))
    }}