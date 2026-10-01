package ru.slem.taskwidget

import java.time.LocalDate

data class MutationRecord(
    val beforeLine: String,
    val afterLine: String,
    val insertedLine: String?,
    val originalLineNumber: Int
)

sealed interface MutationResult {
    data class Success(val text: String, val record: MutationRecord) : MutationResult
    data class Conflict(val unchangedText: String, val reason: String) : MutationResult
    data class Unsupported(val unchangedText: String, val reason: String) : MutationResult
}

object MarkdownMutation {
    private data class Line(var text: String, var ending: String)
    private val checkbox = Regex("""^(\s*[-*+]\s+\[)[ /](\])""")
    private val doneDate = Regex("""✅\s+\d{4}-\d{2}-\d{2}""")

    fun complete(currentText: String, task: ParsedTask, today: LocalDate): MutationResult {
        if (task.status !in setOf(TaskStatus.OPEN, TaskStatus.IN_PROGRESS)) {
            return MutationResult.Conflict(currentText, "Задача уже не активна")
        }
        val lines = split(currentText)
        val matches = lines.indices.filter { lines[it].text == task.originalLine }
        if (matches.size != 1) {
            return MutationResult.Conflict(currentText, "Исходная строка не найдена однозначно")
        }
        val nextLine = if (task.recurrence == null) null else
            RecurrenceEngine.nextLine(task, today) ?: return MutationResult.Unsupported(
                currentText, "Правило повторения не поддержано; откройте задачу в Obsidian"
            )
        val index = matches.single()
        val before = lines[index].text
        val box = checkbox.find(before) ?: return MutationResult.Conflict(currentText, "Checkbox изменён")
        var after = before.replaceRange(box.range, "${box.groupValues[1]}x${box.groupValues[2]}")
        val stamp = "✅ $today"
        after = if (doneDate.containsMatchIn(after)) doneDate.replaceFirst(after, stamp)
            else "$after $stamp"
        lines[index].text = after
        if (nextLine != null) {
            val originalEnding = lines[index].ending
            if (originalEnding.isEmpty()) lines[index].ending = dominantEnding(lines)
            lines.add(index + 1, Line(nextLine, originalEnding))
        }
        return MutationResult.Success(join(lines), MutationRecord(before, after, nextLine, task.lineNumber))
    }

    fun undo(currentText: String, record: MutationRecord): MutationResult {
        val lines = split(currentText)
        val completed = lines.indices.filter { lines[it].text == record.afterLine }
        if (completed.size != 1) {
            return MutationResult.Conflict(currentText, "Завершённая строка изменена или неоднозначна")
        }
        val index = completed.single()
        if (record.insertedLine != null && lines.getOrNull(index + 1)?.text != record.insertedLine) {
            return MutationResult.Conflict(currentText, "Следующее повторение изменено")
        }
        lines[index].text = record.beforeLine
        if (record.insertedLine != null) {
            val inserted = lines.removeAt(index + 1)
            if (inserted.ending.isEmpty()) lines[index].ending = ""
        }
        return MutationResult.Success(join(lines), record)
    }

    private fun dominantEnding(lines: List<Line>): String =
        lines.firstOrNull { it.ending.isNotEmpty() }?.ending ?: "\n"

    private fun split(text: String): MutableList<Line> {
        val lines = mutableListOf<Line>()
        var start = 0
        var index = 0
        while (index < text.length) {
            if (text[index] == '\r' || text[index] == '\n') {
                val end = if (text[index] == '\r' && text.getOrNull(index + 1) == '\n') index + 2 else index + 1
                lines.add(Line(text.substring(start, index), text.substring(index, end)))
                start = end
                index = end
            } else index++
        }
        if (start < text.length || lines.isEmpty()) lines.add(Line(text.substring(start), ""))
        return lines
    }

    private fun join(lines: List<Line>): String = buildString {
        lines.forEach { append(it.text).append(it.ending) }
    }
}