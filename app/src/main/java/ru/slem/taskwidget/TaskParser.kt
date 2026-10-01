package ru.slem.taskwidget

import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class TaskStatus { OPEN, IN_PROGRESS, DONE, CANCELLED }
enum class TaskPriority { HIGHEST, HIGH, MEDIUM, NORMAL, LOW, LOWEST }

data class ParsedTask(
    val status: TaskStatus,
    val description: String,
    val project: String?,
    val tags: Set<String>,
    val start: LocalDate?,
    val scheduled: LocalDate?,
    val due: LocalDate?,
    val done: LocalDate?,
    val recurrence: String?,
    val priority: TaskPriority,
    val lineNumber: Int,
    val originalLine: String
)

object TaskParser {
    private val checkbox = Regex("""^\s*[-*+]\s+\[([ xX/\-])]\s+(.+)$""")
    private val tags = Regex("""(?<![\p{L}\p{N}_/#])#([\p{L}\p{N}_/\-]+)""")
    private val dateToken = Regex("""(?:🛫|⏳|📅|✅)\s+\d{4}-\d{2}-\d{2}""")
    private val recurrenceToken = Regex("""🔁\s*(.*?)(?=\s+(?:🛫|⏳|📅|✅|🔺|⏫|🔼|🔽|⏬|➕)|$)""")
    private val priorities = linkedMapOf(
        "🔺" to TaskPriority.HIGHEST, "⏫" to TaskPriority.HIGH,
        "🔼" to TaskPriority.MEDIUM, "🔽" to TaskPriority.LOW,
        "⏬" to TaskPriority.LOWEST
    )
    private val fence = Regex("""^\s*(`{3,}|~{3,}).*$""")

    fun parseLine(line: String, lineNumber: Int, requiredTag: String): ParsedTask? {
        val match = checkbox.matchEntire(line.trimEnd('\r')) ?: return null
        val body = match.groupValues[2]
        val allTags = tags.findAll(body).map { it.groupValues[1] }.toList()
        val normalizedFilter = requiredTag.trim().removePrefix("#")
        if (normalizedFilter.isBlank() || allTags.none { it.equals(normalizedFilter, ignoreCase = true) }) return null
        val status = when (match.groupValues[1]) {
            " " -> TaskStatus.OPEN
            "/" -> TaskStatus.IN_PROGRESS
            "x", "X" -> TaskStatus.DONE
            "-" -> TaskStatus.CANCELLED
            else -> return null
        }
        val project = allTags.firstOrNull { it.startsWith("project/", ignoreCase = true) }
            ?.substringAfter('/')
        val priority = priorities.entries.firstOrNull { body.contains(it.key) }?.value ?: TaskPriority.NORMAL
        val recurrence = recurrenceToken.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val description = body.replace(recurrenceToken, " ")
            .replace(dateToken, " ")
            .let { source -> priorities.keys.fold(source) { value, symbol -> value.replace(symbol, " ") } }
            .replace(tags, " ")
            .replace(Regex("""\s+"""), " ").trim()
        if (description.isEmpty()) return null
        return ParsedTask(
            status = status, description = description, project = project,
            tags = allTags.toSet(), start = date(body, "🛫"),
            scheduled = date(body, "⏳"), due = date(body, "📅"),
            done = date(body, "✅"), recurrence = recurrence, priority = priority,
            lineNumber = lineNumber, originalLine = line.trimEnd('\r')
        )
    }

    fun parseDocument(markdown: String, requiredTag: String): List<ParsedTask> {
        val result = mutableListOf<ParsedTask>()
        var fenceChar: Char? = null
        var fenceLength = 0
        markdown.lineSequence().forEachIndexed { index, line ->
            val marker = fence.matchEntire(line.trimEnd('\r'))?.groupValues?.get(1)
            if (fenceChar != null) {
                if (marker != null && marker.first() == fenceChar && marker.length >= fenceLength) {
                    fenceChar = null
                }
            } else if (marker != null) {
                fenceChar = marker.first()
                fenceLength = marker.length
            } else {
                parseLine(line, index + 1, requiredTag)?.let(result::add)
            }
        }
        return result
    }

    private fun date(body: String, emoji: String): LocalDate? {
        val value = Regex(Regex.escape(emoji) + """\s+(\d{4}-\d{2}-\d{2})""")
            .find(body)?.groupValues?.get(1) ?: return null
        return try { LocalDate.parse(value) } catch (_: DateTimeParseException) { null }
    }
}