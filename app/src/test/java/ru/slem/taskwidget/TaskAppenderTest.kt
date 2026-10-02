package ru.slem.taskwidget

import org.junit.Assert.*
import org.junit.Test

class TaskAppenderTest {
    @Test fun normalizesConfiguredPathInsideVaultAndRejectsTraversal() {
        assertEquals("sl_work/Tasks/Список задач.md", TaskDestination.normalize(
            "DemoVault\\sl_work\\Tasks\\Список задач.md", "DemoVault"))
        assertNull(TaskDestination.normalize("../outside.md", "DemoVault"))
        assertNull(TaskDestination.normalize("C:\\outside.md", "DemoVault"))
    }

    @Test fun appendsOneTaggedCheckboxWithoutChangingExistingMarkdown() {
        val original = "# Задачи\r\n- [ ] Старая #task\r\n"
        val result = TaskAppender.appendText(original, "Новая", "#task")
        assertEquals(original + "- [ ] Новая #task\r\n", result)
        assertNull(TaskAppender.appendText(original, "bad\nline", "#task"))
        assertEquals("- [ ] План #taskish #task\n",
            TaskAppender.appendText("", "План #taskish", "#task"))
        assertNull(TaskAppender.appendText("", "#task", "#task"))
    }
}