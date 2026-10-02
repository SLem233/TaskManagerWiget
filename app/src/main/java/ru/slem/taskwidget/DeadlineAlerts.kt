package ru.slem.taskwidget

import java.time.LocalDate

enum class DeadlineSeverity { NONE, TODAY, OVERDUE }

data class DeadlineSummary(val dueToday: Int, val overdue: Int) {
    val severity: DeadlineSeverity get() = when {
        overdue > 0 -> DeadlineSeverity.OVERDUE
        dueToday > 0 -> DeadlineSeverity.TODAY
        else -> DeadlineSeverity.NONE
    }
    val color: Int get() = when (severity) {
        DeadlineSeverity.OVERDUE -> 0xFFFF0000.toInt()
        DeadlineSeverity.TODAY -> 0xFFFF8040.toInt()
        DeadlineSeverity.NONE -> 0xFFFFFFFF.toInt()
    }
}

object DeadlineAlerts {
    fun summarize(tasks: List<IndexedTask>, today: LocalDate): DeadlineSummary {
        val active = tasks.map { it.task }.filter {
            it.status == TaskStatus.OPEN || it.status == TaskStatus.IN_PROGRESS
        }
        return DeadlineSummary(
            dueToday = active.count { it.due == today },
            overdue = active.count { it.due?.isBefore(today) == true }
        )
    }
}