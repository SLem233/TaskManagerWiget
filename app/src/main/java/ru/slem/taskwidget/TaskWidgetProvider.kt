package ru.slem.taskwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast

class TaskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
        VaultScanJobService.ensurePeriodic(context)
        DeadlineNotifications.update(context, VaultRepository.indexedTasks(context))
    }

    override fun onDisabled(context: Context) {
        VaultScanJobService.cancelPeriodic(context)
        DeadlineNotifications.clear(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        render(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in setOf(ACTION_ROW, ACTION_REFRESH)) return
        if (!WidgetPreferences.validToken(context, intent.getStringExtra(EXTRA_TOKEN))) return
        when (intent.action) {
            ACTION_REFRESH -> VaultScanJobService.scheduleNow(context)
            ACTION_ROW -> {
                val rowId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                if (intent.getBooleanExtra(EXTRA_OPEN, false)) {
                    openNote(context, rowId)
                } else {
                    runAsync(context) {
                        try {
                            val outcome = VaultRepository.complete(context, rowId)
                            if (outcome !is WriteOutcome.Success) message(context, reason(outcome))
                        } finally { VaultScanJobService.scheduleNow(context) }
                    }
                }
            }
        }
    }

    private fun runAsync(context: Context, work: () -> Unit) {
        val pending = goAsync()
        Thread {
            try { work() }
            catch (error: Exception) { message(context, "Ошибка Vault: ${error.message}") }
            finally {
                try { refreshAll(context) } finally { pending.finish() }
            }
        }.start()
    }

    private fun openNote(context: Context, rowId: String) {
        val task = WidgetAgenda.find(VaultRepository.indexedTasks(context), rowId) ?: return
        val vault = WidgetPreferences.vaultName(context)
        val path = task.path.removeSuffix(".md")
        val link = Uri.Builder().scheme("obsidian").authority("open")
            .appendQueryParameter("vault", vault).appendQueryParameter("file", path).build()
        val intent = Intent(Intent.ACTION_VIEW, link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) }
        catch (_: Exception) { message(context, "Не удалось открыть заметку в Obsidian") }
    }

    private fun reason(outcome: WriteOutcome): String = when (outcome) {
        is WriteOutcome.Success -> "Готово"
        is WriteOutcome.Conflict -> outcome.reason
        is WriteOutcome.Unsupported -> outcome.reason
        is WriteOutcome.Failure -> outcome.reason
    }

    private fun message(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val adapter = Intent(context, AgendaViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            data = Uri.parse("taskwidget://agenda/$id")
        }
        val views = RemoteViews(context.packageName, R.layout.widget_layout).apply {
            setInt(R.id.widget_root, "setBackgroundColor",
                Color.argb(WidgetPreferences.opacity(context, id) * 255 / 100, 0, 0, 0))
            setTextViewText(R.id.widget_title, if (WidgetPreferences.lastScanError(context) == null)
                "ЗАДАЧИ" else "ЗАДАЧИ ⚠")
            setTextViewText(R.id.widget_status, when (WidgetPreferences.refreshState(context)) {
                WidgetRefreshState.IDLE -> ""
                WidgetRefreshState.RUNNING -> "Обновляю..."
                WidgetRefreshState.SUCCESS -> "Обновлено"
                WidgetRefreshState.FAILURE -> "Ошибка обновления"
            })
            setViewVisibility(R.id.widget_status,
                if (WidgetPreferences.refreshState(context) == WidgetRefreshState.IDLE)
                    android.view.View.GONE else android.view.View.VISIBLE)
            setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(
                context, id + 10_000, Intent(context, AddTaskActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            setRemoteAdapter(R.id.task_list, adapter)
            setPendingIntentTemplate(R.id.task_list, PendingIntent.getBroadcast(
                context, id, Intent(context, TaskWidgetProvider::class.java).apply {
                    action = ACTION_ROW
                    putExtra(EXTRA_TOKEN, WidgetPreferences.token(context))
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            ))
            setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(
                context, id, Intent(context, TaskWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(EXTRA_TOKEN, WidgetPreferences.token(context))
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            setOnClickPendingIntent(R.id.widget_settings, PendingIntent.getActivity(
                context, id, Intent(context, MainActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
        }
        manager.updateAppWidget(id, views)
    }

    companion object {
        const val ACTION_ROW = "ru.slem.taskwidget.ACTION_ROW"
        const val ACTION_REFRESH = "ru.slem.taskwidget.ACTION_REFRESH"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_OPEN = "open_note"
        const val EXTRA_TOKEN = "widget_token"

        fun refreshAll(context: Context) {
            DeadlineNotifications.update(context, VaultRepository.indexedTasks(context))
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
            ids.forEach { TaskWidgetProvider().render(context, manager, it) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.task_list)
            if (WidgetPreferences.refreshState(context) == WidgetRefreshState.SUCCESS) {
                val appContext = context.applicationContext
                Handler(Looper.getMainLooper()).postDelayed({
                    if (WidgetPreferences.refreshState(appContext) == WidgetRefreshState.IDLE) {
                        WidgetPreferences.setRefreshState(appContext, WidgetRefreshState.IDLE)
                        refreshAll(appContext)
                    }
                }, WidgetPreferences.SUCCESS_VISIBLE_MS + 100L)
            }
        }
    }
}