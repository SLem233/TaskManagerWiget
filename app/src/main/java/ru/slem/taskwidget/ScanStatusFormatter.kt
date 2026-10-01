package ru.slem.taskwidget

object ScanStatusFormatter {
    fun format(progress: ScanProgress, elapsedMillis: Long): String {
        val seconds = (elapsedMillis / 1_000).coerceAtLeast(0)
        val duration = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        val current = progress.currentPath?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }?.let { "\nСейчас: $it" }.orEmpty()
        return "Проверено файлов: ${progress.filesRead + progress.filesReused} · задач: ${progress.tasksFound}" +
            "\nиз индекса: ${progress.filesReused}" +
            "\nПапок: ${progress.foldersRead} · время: $duration$current"
    }
}