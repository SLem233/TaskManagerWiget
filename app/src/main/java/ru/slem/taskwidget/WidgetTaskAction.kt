package ru.slem.taskwidget

import java.time.LocalDate

object WidgetTaskAction {
    fun complete(
        snapshot: IndexSnapshot?, vaultKey: String, requiredTag: String, rowId: String,
        access: MarkdownDocumentAccess, journal: TaskWriteJournal, today: LocalDate
    ): WriteOutcome {
        if (snapshot == null || snapshot.vaultKey != vaultKey || snapshot.requiredTag != requiredTag) {
            return WriteOutcome.Conflict("Индекс Vault устарел; обновите виджет")
        }
        val tasks = snapshot.files.values.flatMap { file ->
            file.tasks.map { IndexedTask(file.path, file.id, file.lastModified, it) }
        }
        val task = WidgetAgenda.find(tasks, rowId)
            ?: return WriteOutcome.Conflict("Задача изменилась; обновите виджет")
        return TaskWriter.complete(task, access, journal, today)
    }
}