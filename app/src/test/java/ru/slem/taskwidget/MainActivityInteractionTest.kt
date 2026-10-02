package ru.slem.taskwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.DialogInterface
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36])
class MainActivityInteractionTest {
    private fun descendants(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) {
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
        } else emptyList()

    private fun button(activity: MainActivity, title: String): Button =
        descendants(activity.window.decorView).filterIsInstance<Button>()
            .single { it.text.toString() == title }

    private fun texts(activity: MainActivity): List<String> =
        descendants(activity.window.decorView).filterIsInstance<TextView>()
            .map { it.text.toString() }

    @Test fun widgetSettingsPersistAndInvalidTagIsRejected() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 12)
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        val views = descendants(activity.window.decorView)
        val sliders = views.filterIsInstance<SeekBar>()
        assertEquals(2, sliders.size)
        sliders[0].progress = 7
        sliders[1].progress = 35
        button(activity, "Сохранить вид виджета").performClick()
        assertEquals(19f, WidgetPreferences.fontSize(activity, 12))
        assertEquals(35, WidgetPreferences.opacity(activity, 12))

        button(activity, "Сканировать Vault").performClick()
        assertTrue(texts(activity).any { it.contains("Сначала выберите") })
        activity.getSharedPreferences(WidgetPreferences.SETTINGS, 0).edit()
            .putString(WidgetPreferences.VAULT_URI, "content://synthetic.vault/tree/root").commit()
        views.filterIsInstance<EditText>().single { it.text.toString().startsWith("#") }.setText("bad tag")
        button(activity, "Сканировать Vault").performClick()
        assertTrue(texts(activity).any { it.contains("Укажите один тег") })
        button(activity, "Выбрать папку Vault").performClick()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,
            shadowOf(activity).nextStartedActivityForResult.intent.action)
        activity.finish()
    }

    @Test fun taskFileSettingUsesVaultRelativePathAndRejectsTraversal() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(WidgetPreferences.SETTINGS, 0).edit()
            .putString(WidgetPreferences.VAULT_NAME, "DemoVault").commit()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val input = descendants(activity.window.decorView).filterIsInstance<EditText>()
            .single { it.text.toString().contains("Список задач.md") }
        input.setText("DemoVault\\sl_work\\Tasks\\Список задач.md")
        button(activity, "Сохранить файл новых задач").performClick()
        assertEquals("sl_work/Tasks/Список задач.md", WidgetPreferences.addFile(activity))
        input.setText("../outside.md")
        button(activity, "Сохранить файл новых задач").performClick()
        assertEquals("sl_work/Tasks/Список задач.md", WidgetPreferences.addFile(activity))
        activity.finish()
    }
    @Test fun settingsScreenRemainsSeparateFromTaskCreation() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertTrue(texts(activity).contains("Задачи · настройка"))
        assertFalse(texts(activity).contains("Новая задача"))
        activity.finish()
    }
    @Test fun journalShowsRecoveryExportAndCanClearResolvedHistory() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(WidgetPreferences.SETTINGS, 0).edit()
            .putString(WidgetPreferences.VAULT_URI, "content://synthetic.vault/tree/root").commit()
        val record = MutationRecord("before", "after", null, 1)
        LocalTaskWriteJournal(context, "content://synthetic.vault/tree/root").use { journal ->
            val done = journal.prepare("doc", "Done.md", record, "before".toByteArray())
            journal.setStatus(done.id, JournalStatus.DONE)
            journal.prepare("other", "Recover.md", record, "backup".toByteArray())
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(button(activity, "Отменить"))
        assertNotNull(button(activity, "Экспортировать резервную копию"))
        button(activity, "Экспортировать резервную копию").performClick()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,
            shadowOf(activity).nextStartedActivityForResult.intent.action)
        button(activity, "Очистить завершённую историю").performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        LocalTaskWriteJournal(context, "content://synthetic.vault/tree/root").use { journal ->
            assertEquals(1, journal.recent().size)
            assertEquals(JournalStatus.PREPARED, journal.recent().single().status)
        }
        activity.finish()
    }
}