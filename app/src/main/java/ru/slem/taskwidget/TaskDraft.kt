package ru.slem.taskwidget

import java.time.LocalDate

data class TaskDraft(
    val description: String,
    val tags: String,
    val start: LocalDate? = null,
    val scheduled: LocalDate? = null,
    val due: LocalDate? = null
) {
    fun markdownLine(requiredTag: String): String? {
        val title = description.trim()
        if (title.isEmpty() || title.any { it == '\n' || it == '\r' || it.isISOControl() }) return null
        val tagPattern = Regex("""#[\p{L}\p{N}_/\-]+""")
        if (!tagPattern.matches(requiredTag)) return null
        val supplied = tags.trim().takeIf { it.isNotEmpty() }
            ?.split(Regex("""\s+""")).orEmpty()
        if (supplied.any { !tagPattern.matches(it) }) return null
        val embedded = tagPattern.findAll(title).map { it.value.lowercase() }.toSet()
        val allTags = (listOf(requiredTag) + supplied).distinctBy { it.lowercase() }
            .filterNot { it.lowercase() in embedded }
        val line = buildString {
            append("- [ ] ").append(title)
            if (allTags.isNotEmpty()) append(' ').append(allTags.joinToString(" "))
            start?.let { append(" 🛫 ").append(it) }
            scheduled?.let { append(" ⏳ ").append(it) }
            due?.let { append(" 📅 ").append(it) }
        }
        return line.takeIf { TaskParser.parseLine(it, 1, requiredTag) != null }
    }
}