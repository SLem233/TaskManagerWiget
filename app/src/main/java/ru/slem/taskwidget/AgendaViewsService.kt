package ru.slem.taskwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDate

class AgendaViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        AgendaFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
}

private class AgendaFactory(
    private val context: Context, private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<WidgetRow> = emptyList()

    override fun onCreate() = onDataSetChanged()
    override fun onDataSetChanged() {
        rows = WidgetAgenda.build(VaultRepository.indexedTasks(context), LocalDate.now(), 14)
    }
    override fun onDestroy() = Unit
    override fun getCount(): Int = rows.size
    override fun getViewAt(position: Int): RemoteViews? {
        val row = rows.getOrNull(position) ?: return null
        return when (row) {
            is WidgetRow.Header -> RemoteViews(context.packageName, R.layout.widget_date).apply {
                setTextViewText(R.id.date_title, row.label)
            }
            is WidgetRow.Task -> RemoteViews(context.packageName, R.layout.widget_task).apply {
                setTextViewText(R.id.task_title, row.title)
                setTextColor(R.id.task_title, row.titleColor)
                setTextViewText(R.id.task_detail, row.detail)
                setTextViewTextSize(R.id.task_title, TypedValue.COMPLEX_UNIT_SP,
                    WidgetPreferences.fontSize(context, widgetId))
                setOnClickFillInIntent(R.id.task_checkbox, Intent().apply {
                    putExtra(TaskWidgetProvider.EXTRA_TASK_ID, row.id)
                })
                setOnClickFillInIntent(R.id.task_title, Intent().apply {
                    putExtra(TaskWidgetProvider.EXTRA_TASK_ID, row.id)
                    putExtra(TaskWidgetProvider.EXTRA_OPEN, true)
                })
            }
        }
    }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 2
    override fun getItemId(position: Int): Long = when (val row = rows[position]) {
        is WidgetRow.Header -> ("header-" + row.label).hashCode().toLong()
        is WidgetRow.Task -> row.id.take(16).toULong(16).toLong()
    }
    override fun hasStableIds(): Boolean = true
}