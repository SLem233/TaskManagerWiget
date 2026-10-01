package ru.slem.taskwidget

import java.time.LocalDate

sealed interface AgendaGroupKey {
    data object Overdue : AgendaGroupKey
    data object Today : AgendaGroupKey
    data object Tomorrow : AgendaGroupKey
    data class Date(val day: LocalDate) : AgendaGroupKey
    data object Undated : AgendaGroupKey
}

data class AgendaGroup(val key: AgendaGroupKey, val tasks: List<IndexedTask>)

object AgendaProjector {
    fun project(tasks: List<IndexedTask>, today: LocalDate, horizonDays: Int): List<AgendaGroup> {
        require(horizonDays > 0)
        val lastDate = today.plusDays((horizonDays - 1).toLong())
        val grouped = linkedMapOf<AgendaGroupKey, MutableList<IndexedTask>>()
        for (indexed in tasks) {
            if (indexed.task.status == TaskStatus.DONE || indexed.task.status == TaskStatus.CANCELLED) continue
            val date = indexed.task.scheduled ?: indexed.task.due ?: indexed.task.start
            val key = when {
                indexed.task.due?.isBefore(today) == true -> AgendaGroupKey.Overdue
                date == null -> AgendaGroupKey.Undated
                date.isBefore(today) || date == today -> AgendaGroupKey.Today
                date.isAfter(lastDate) -> continue
                date == today.plusDays(1) -> AgendaGroupKey.Tomorrow
                else -> AgendaGroupKey.Date(date)
            }
            grouped.getOrPut(key) { mutableListOf() }.add(indexed)
        }
        val taskOrder = compareBy<IndexedTask> { it.task.priority.ordinal }
            .thenBy { it.task.due ?: LocalDate.MAX }
            .thenBy { it.path }
            .thenBy { it.task.lineNumber }
        return grouped.entries.sortedWith(compareBy({ groupRank(it.key) }, { groupDate(it.key) }))
            .map { AgendaGroup(it.key, it.value.sortedWith(taskOrder)) }
    }

    private fun groupRank(key: AgendaGroupKey): Int = when (key) {
        AgendaGroupKey.Overdue -> 0
        AgendaGroupKey.Today -> 1
        AgendaGroupKey.Tomorrow -> 2
        is AgendaGroupKey.Date -> 3
        AgendaGroupKey.Undated -> 4
    }

    private fun groupDate(key: AgendaGroupKey): LocalDate =
        if (key is AgendaGroupKey.Date) key.day else LocalDate.MIN
}