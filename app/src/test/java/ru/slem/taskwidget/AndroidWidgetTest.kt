package ru.slem.taskwidget

import android.Manifest
import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import android.widget.FrameLayout
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36])
class AndroidWidgetTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun prepareIndex(
        line: String = "- [ ] Обсудить план #task 📅 2026-10-02"
    ): String {
        val uri = "content://synthetic.vault/tree/root"
        val task = requireNotNull(TaskParser.parseLine(line, 1, "#task"))
        LocalTaskIndex(context).save(IndexSnapshot(uri, "#task",
            mapOf("doc" to CachedFile("doc", "Folder/Note.md", 1, 10, listOf(task)))))
        context.getSharedPreferences(WidgetPreferences.SETTINGS, 0).edit()
            .putString(WidgetPreferences.VAULT_URI, uri)
            .putString(WidgetPreferences.VAULT_NAME, "Demo")
            .putString(WidgetPreferences.TASK_TAG, "#task").commit()
        return (WidgetAgenda.build(VaultRepository.indexedTasks(context),
            LocalDate.of(2026, 10, 1), 14)[1] as WidgetRow.Task).id
    }

    @Test fun remoteViewsFactoryDisplaysIndexedTaskAndStableRows() {
        prepareIndex()
        val service = Robolectric.buildService(AgendaViewsService::class.java).create().get()
        val factory = service.onGetViewFactory(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7))
        factory.onCreate()
        assertEquals(2, factory.count)
        assertEquals(2, factory.viewTypeCount)
        assertNotNull(factory.getViewAt(0))
        assertNotNull(factory.getViewAt(1))
        assertNotEquals(factory.getItemId(0), factory.getItemId(1))
        assertTrue(factory.hasStableIds())
        factory.onDestroy()
    }

    @Test fun remoteViewsPaintsOverdueTaskTitleRed() {
        prepareIndex("- [ ] Просрочено #task 📅 2020-01-01")
        val service = Robolectric.buildService(AgendaViewsService::class.java).create().get()
        val factory = service.onGetViewFactory(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 7))
        factory.onCreate()
        val view = requireNotNull(factory.getViewAt(1)).apply(context, FrameLayout(context))
        assertEquals(0xFFFF0000.toInt(),
            view.findViewById<TextView>(R.id.task_title).currentTextColor)
    }

    @Test fun widgetRendersAndRefreshRequiresValidToken() {
        prepareIndex()
        val manager = AppWidgetManager.getInstance(context)
        val id = shadowOf(manager).createWidget(TaskWidgetProvider::class.java, R.layout.widget_layout)
        val provider = TaskWidgetProvider()
        provider.onUpdate(context, manager, intArrayOf(id))
        val widget = shadowOf(manager).getViewFor(id)
        assertEquals("ЗАДАЧИ", widget.findViewById<TextView>(R.id.widget_title).text.toString())
        assertTrue(context.getSystemService(JobScheduler::class.java).allPendingJobs.isNotEmpty())

        WidgetPreferences.setScanError(context, "Ошибка")
        TaskWidgetProvider.refreshAll(context)
        assertEquals("ЗАДАЧИ ⚠", widget.findViewById<TextView>(R.id.widget_title).text.toString())
        provider.onAppWidgetOptionsChanged(context, manager, id, Bundle())
        provider.onDisabled(context)

        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancelAll()
        provider.onReceive(context, Intent(context, TaskWidgetProvider::class.java)
            .setAction(TaskWidgetProvider.ACTION_REFRESH))
        assertTrue(scheduler.allPendingJobs.isEmpty())
        provider.onReceive(context, Intent(context, TaskWidgetProvider::class.java)
            .setAction(TaskWidgetProvider.ACTION_REFRESH)
            .putExtra(TaskWidgetProvider.EXTRA_TOKEN, WidgetPreferences.token(context)))
        assertEquals(1, scheduler.allPendingJobs.size)
    }

    @Test fun headerTitleAndActionIconsShareVerticalCenter() {
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.widget_layout, null)
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 400)
        fun center(id: Int): Int {
            val item = view.findViewById<android.view.View>(id)
            return item.top + item.height / 2
        }
        assertSame(view.findViewById<android.view.View>(R.id.widget_refresh).parent, view.findViewById<android.view.View>(R.id.widget_title).parent)
        assertEquals(center(R.id.widget_refresh), center(R.id.widget_title))
        assertEquals(center(R.id.widget_settings), center(R.id.widget_title))
    }
    @Test fun headerActionsUseEqualCenteredDrawablesInsteadOfFontGlyphs() {
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.widget_layout, null)
        val actions = listOf(R.id.widget_add, R.id.widget_refresh, R.id.widget_settings)
            .map { view.findViewById<android.widget.ImageView>(it) }
        assertEquals(1, actions.map { it.parent }.distinct().size)
        assertEquals(1, actions.map { it.layoutParams.height }.distinct().size)
        assertEquals(1, actions.map { it.paddingTop to it.paddingBottom }.distinct().size)
        assertTrue(actions.all { it.drawable != null })
    }

    @Test fun plusStartsDedicatedTaskScreen() {
        prepareIndex()
        val manager = AppWidgetManager.getInstance(context)
        val id = shadowOf(manager).createWidget(TaskWidgetProvider::class.java, R.layout.widget_layout)
        TaskWidgetProvider().onUpdate(context, manager, intArrayOf(id))
        val widget = shadowOf(manager).getViewFor(id)
        widget.findViewById<android.view.View>(R.id.widget_add).performClick()
        assertEquals("ru.slem.taskwidget.AddTaskActivity",
            shadowOf(context as Application).nextStartedActivity.component?.className)
    }

    @Test @Config(sdk = [28]) fun openingAppRefreshesDeadlineBellFromIndex() {
        val task = requireNotNull(TaskParser.parseLine(
            "- [ ] Просрочено #task 📅 2020-01-01", 1, "#task"))
        val uri = "content://synthetic.vault/tree/root"
        LocalTaskIndex(context).save(IndexSnapshot(uri, "#task",
            mapOf("doc" to CachedFile("doc", "Note.md", 1, 10, listOf(task)))))
        context.getSharedPreferences(WidgetPreferences.SETTINGS, 0).edit()
            .putString(WidgetPreferences.VAULT_URI, uri)
            .putString(WidgetPreferences.TASK_TAG, "#task").commit()
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.cancelAll()
        Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(1, manager.activeNotifications.size)
    }
    @Test @Config(sdk = [28]) fun notificationBellUsesWidgetDueColorsAndClearsWhenResolved() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val today = LocalDate.of(2026, 10, 2)
        fun indexed(line: String) = IndexedTask("List.md", "doc", 1,
            requireNotNull(TaskParser.parseLine(line, 1, "#task")))
        DeadlineNotifications.update(context,
            listOf(indexed("- [ ] Сегодня #task 📅 2026-10-02")), today)
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(0xFFFF8040.toInt(), manager.activeNotifications.single().notification.color)
        val todayIcon = manager.activeNotifications.single().notification.smallIcon.resId
        DeadlineNotifications.update(context,
            listOf(indexed("- [ ] Вчера #task 📅 2026-10-01")), today)
        assertEquals(0xFFFF0000.toInt(), manager.activeNotifications.single().notification.color)
        assertNotEquals(todayIcon, manager.activeNotifications.single().notification.smallIcon.resId)
        DeadlineNotifications.update(context, emptyList(), today)
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test @Config(sdk = [28]) fun removingLastWidgetClearsDeadlineBell() {
        val today = LocalDate.of(2026, 10, 2)
        val task = IndexedTask("List.md", "doc", 1,
            requireNotNull(TaskParser.parseLine("- [ ] Вчера #task 📅 2026-10-01", 1, "#task")))
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        DeadlineNotifications.update(context, listOf(task), today)
        assertEquals(1, manager.activeNotifications.size)
        TaskWidgetProvider().onDisabled(context)
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun periodicScanPersistsAcrossRebootAndReplacesOldSchedule() {
        prepareIndex()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val old = JobInfo.Builder(4102, ComponentName(context, VaultScanJobService::class.java))
            .setPeriodic(30L * 60L * 1000L).build()
        assertEquals(JobScheduler.RESULT_SUCCESS, scheduler.schedule(old))
        assertFalse(requireNotNull(scheduler.getPendingJob(4102)).isPersisted)

        VaultScanJobService.ensurePeriodic(context)
        val replacement = requireNotNull(scheduler.getPendingJob(4102))
        assertTrue(replacement.isPeriodic)
        assertTrue(replacement.isPersisted)
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertTrue(permissions.contains(Manifest.permission.RECEIVE_BOOT_COMPLETED))
    }

    @Test fun refreshImmediatelyShowsProgressOnlyForAuthorizedClick() {
        prepareIndex()
        val manager = AppWidgetManager.getInstance(context)
        val id = shadowOf(manager).createWidget(TaskWidgetProvider::class.java, R.layout.widget_layout)
        val provider = TaskWidgetProvider()
        provider.onUpdate(context, manager, intArrayOf(id))
        val widget = shadowOf(manager).getViewFor(id)
        val statusId = context.resources.getIdentifier("widget_status", "id", context.packageName)
        assertNotEquals(0, statusId)
        val status = widget.findViewById<TextView>(statusId)
        assertEquals("", status.text.toString())

        provider.onReceive(context, Intent(context, TaskWidgetProvider::class.java)
            .setAction(TaskWidgetProvider.ACTION_REFRESH))
        assertEquals("", status.text.toString())
        provider.onReceive(context, Intent(context, TaskWidgetProvider::class.java)
            .setAction(TaskWidgetProvider.ACTION_REFRESH)
            .putExtra(TaskWidgetProvider.EXTRA_TOKEN, WidgetPreferences.token(context)))
        assertEquals("Обновляю...", status.text.toString())
    }

    @Test fun refreshResultShowsSuccessFailureAndDoesNotStayRunningForever() {
        prepareIndex()
        val manager = AppWidgetManager.getInstance(context)
        val id = shadowOf(manager).createWidget(TaskWidgetProvider::class.java, R.layout.widget_layout)
        TaskWidgetProvider().onUpdate(context, manager, intArrayOf(id))
        val status = shadowOf(manager).getViewFor(id).findViewById<TextView>(R.id.widget_status)

        WidgetPreferences.setRefreshState(context, WidgetRefreshState.SUCCESS)
        TaskWidgetProvider.refreshAll(context)
        assertEquals("Обновлено", status.text.toString())
        WidgetPreferences.setRefreshState(context, WidgetRefreshState.FAILURE)
        TaskWidgetProvider.refreshAll(context)
        assertEquals("Ошибка обновления", status.text.toString())

        WidgetPreferences.setRefreshState(context, WidgetRefreshState.RUNNING)
        assertEquals(WidgetRefreshState.FAILURE,
            WidgetPreferences.refreshState(context, System.currentTimeMillis() + 11 * 60_000))
    }

    @Test fun successFeedbackDisappearsFromWidgetAfterFiveSeconds() {
        prepareIndex()
        val manager = AppWidgetManager.getInstance(context)
        val id = shadowOf(manager).createWidget(TaskWidgetProvider::class.java, R.layout.widget_layout)
        TaskWidgetProvider().onUpdate(context, manager, intArrayOf(id))
        val status = shadowOf(manager).getViewFor(id).findViewById<TextView>(R.id.widget_status)
        WidgetPreferences.setRefreshState(context, WidgetRefreshState.SUCCESS)
        TaskWidgetProvider.refreshAll(context)
        assertEquals("Обновлено", status.text.toString())
        shadowOf(Looper.getMainLooper()).idleFor(6, TimeUnit.SECONDS)
        assertEquals(WidgetRefreshState.IDLE, WidgetPreferences.refreshState(context))
        assertEquals("", status.text.toString())
    }

    @Test fun taskTextOpensIndexedNoteAndForgedIdDoesNot() {
        val rowId = prepareIndex()
        val provider = TaskWidgetProvider()
        val base = Intent(context, TaskWidgetProvider::class.java)
            .setAction(TaskWidgetProvider.ACTION_ROW)
            .putExtra(TaskWidgetProvider.EXTRA_TOKEN, WidgetPreferences.token(context))
            .putExtra(TaskWidgetProvider.EXTRA_OPEN, true)
        provider.onReceive(context, Intent(base).putExtra(TaskWidgetProvider.EXTRA_TASK_ID, "forged"))
        assertNull(shadowOf(context as Application).nextStartedActivity)
        provider.onReceive(context, Intent(base).putExtra(TaskWidgetProvider.EXTRA_TASK_ID, rowId))
        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals("obsidian", started.data?.scheme)
        assertEquals("open", started.data?.authority)
        assertEquals("Demo", started.data?.getQueryParameter("vault"))
        assertEquals("Folder/Note", started.data?.getQueryParameter("file"))
    }
}