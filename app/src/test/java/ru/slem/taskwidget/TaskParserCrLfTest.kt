package ru.slem.taskwidget

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskParserCrLfTest {
    @Test fun originalLineExcludesLineEndingForSafeWrite() {
        val line = "- [ ] Дело #task"
        val task = TaskParser.parseDocument("Заголовок\r\n$line\r\n", "#task").single()
        assertEquals(line, task.originalLine)
        assertEquals(2, task.lineNumber)
    }
}