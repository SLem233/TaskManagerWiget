package ru.slem.taskwidget

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.LocalDate

sealed interface WidgetRow {
    data class Header(val label: String) : WidgetRow
    data class Task(
        val id: String, val title: String, val detail: String,
        val path: String, val titleColor: Int
    ) : WidgetRow
}

object WidgetAgenda {
    private val months = listOf("янв.", "февр.", "мар.", "апр.", "мая", "июн.",
        "июл.", "авг.", "сент.", "окт.", "нояб.", "дек.")

    fun build(tasks: List<IndexedTask>, today: LocalDate, horizonDays: Int): List<WidgetRow> =
        buildList {
            for (group in AgendaProjector.project(tasks, today, horizonDays)) {
                add(WidgetRow.Header(label(group.key)))
                for (indexed in group.tasks) {
                    val task = indexed.task
                    val detail = buildList {
                        task.project?.let(::add)
                        task.due?.let { add("до ${date(it)}") }
                        if (isEmpty()) add(indexed.path.substringAfterLast('/'))
                    }.joinToString(" · ")
                    add(WidgetRow.Task(id(indexed), task.description, detail, indexed.path,
                        titleColor(task, today)))
                }
            }
        }

    private fun titleColor(task: ParsedTask, today: LocalDate): Int = when {
        task.due?.isBefore(today) == true -> 0xFFFF0000.toInt()
        task.due == today -> 0xFFFF8040.toInt()
        task.scheduled?.let { !it.isAfter(today) } == true -> 0xFFFFFF80.toInt()
        task.start?.let { !it.isAfter(today) } == true -> 0xFF0080FF.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    fun find(tasks: List<IndexedTask>, id: String): IndexedTask? =
        tasks.filter { it.task.status != TaskStatus.DONE && it.task.status != TaskStatus.CANCELLED }
            .singleOrNull { id(it) == id }

    private fun id(task: IndexedTask): String {
        val source = "${task.nodeId}\u0000${task.task.lineNumber}\u0000${task.task.originalLine}"
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun label(key: AgendaGroupKey): String = when (key) {
        AgendaGroupKey.Overdue -> "Просрочено"
        AgendaGroupKey.Today -> "Сегодня"
        AgendaGroupKey.Tomorrow -> "Завтра"
        is AgendaGroupKey.Date -> date(key.day)
        AgendaGroupKey.Undated -> "Без даты"
    }

    private fun date(day: LocalDate): String = "${day.dayOfMonth} ${months[day.monthValue - 1]}"
}