package ru.slem.taskwidget

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

object RecurrenceEngine {
    private val rulePattern = Regex(
        """^every\s+(?:(\d+)\s+)?(day|days|week|weeks|month|months|year|years)(\s+when done)?$""",
        RegexOption.IGNORE_CASE
    )
    private val dates = Regex("""(🛫|⏳|📅)\s+(\d{4}-\d{2}-\d{2})""")
    private val doneDate = Regex("""\s*✅\s+\d{4}-\d{2}-\d{2}""")
    private val box = Regex("""^(\s*[-*+]\s+\[)[ /](\])""")

    fun nextLine(task: ParsedTask, completedOn: LocalDate): String? {
        val rule = task.recurrence ?: return null
        val match = rulePattern.matchEntire(rule.trim()) ?: return null
        val amount = match.groupValues[1].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1
        if (amount !in 1..10_000) return null
        val period = when (match.groupValues[2].lowercase().removeSuffix("s")) {
            "day" -> Period.ofDays(amount)
            "week" -> Period.ofWeeks(amount)
            "month" -> Period.ofMonths(amount)
            "year" -> Period.ofYears(amount)
            else -> return null
        }
        val anchor = task.due ?: task.scheduled ?: task.start ?: return null
        val whenDone = match.groupValues[3].isNotBlank()
        val nextAnchor = (if (whenDone) completedOn else anchor).plus(period)
        val shiftDays = ChronoUnit.DAYS.between(anchor, nextAnchor)
        val original = task.originalLine.replace(doneDate, "")
        val status = box.find(original) ?: return null
        val reopened = original.replaceRange(status.range, "${status.groupValues[1]} ${status.groupValues[2]}")
        return try {
            dates.replace(reopened) { dateMatch ->
                val date = LocalDate.parse(dateMatch.groupValues[2]).plusDays(shiftDays)
                "${dateMatch.groupValues[1]} $date"
            }
        } catch (_: Exception) {
            null
        }
    }
}