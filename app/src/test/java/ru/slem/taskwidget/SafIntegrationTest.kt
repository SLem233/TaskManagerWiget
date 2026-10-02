package ru.slem.taskwidget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class SyntheticVaultProvider : ContentProvider() {
    lateinit var markdown: File

    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String = "text/markdown"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cols = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val children = uri.pathSegments.lastOrNull() == "children"
        val id = if (children) "doc" else "root"
        val row = Array<Any?>(cols.size) { index ->
            when (cols[index]) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> if (children) "Note.md" else "Demo"
                DocumentsContract.Document.COLUMN_MIME_TYPE ->
                    if (children) "text/markdown" else DocumentsContract.Document.MIME_TYPE_DIR
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> markdown.lastModified()
                DocumentsContract.Document.COLUMN_SIZE -> markdown.length()
                else -> null
            }
        }
        return MatrixCursor(cols).apply { addRow(row) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val flags = if (mode.contains('w')) {
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE
        } else ParcelFileDescriptor.MODE_READ_ONLY
        return ParcelFileDescriptor.open(markdown, flags)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 36], shadows = [WindowsAtomicFileShadow::class])
class SafIntegrationTest {
    private fun syntheticDocument(context: android.content.Context, markdown: String): File {
        val info = ProviderInfo().apply { authority = "synthetic.vault" }
        val provider = Robolectric.buildContentProvider(SyntheticVaultProvider::class.java)
            .create(info).get()
        val file = File(context.filesDir, "synthetic-note.md").apply { writeText(markdown) }
        provider.markdown = file
        context.getSharedPreferences(WidgetPreferences.SETTINGS, 0).edit()
            .putString(WidgetPreferences.VAULT_URI, "content://synthetic.vault/tree/root")
            .putString(WidgetPreferences.VAULT_NAME, "Demo")
            .putString(WidgetPreferences.TASK_TAG, "#task").commit()
        return file
    }

    @Test fun addsTaskToConfiguredSafFileAndUpdatesIndex() {
        val context = RuntimeEnvironment.getApplication()
        val file = syntheticDocument(context, "# Задачи\n")
        assertTrue(WidgetPreferences.saveAddFile(context, "Note.md"))
        VaultRepository.scan(context)
        assertTrue(VaultRepository.add(context, "Новая задача") is WriteOutcome.Success)
        assertEquals("# Задачи\n- [ ] Новая задача #task\n", file.readText())
        assertEquals("Новая задача", VaultRepository.indexedTasks(context).single().task.description)
    }

    @Test fun structuredTaskWithTagsAndDatesIsSavedThroughSaf() {
        val context = RuntimeEnvironment.getApplication()
        val file = syntheticDocument(context, "# Задачи\n")
        assertTrue(WidgetPreferences.saveAddFile(context, "Note.md"))
        val draft = TaskDraft("Подготовить письмо", "#project/demo #urgent",
            start = LocalDate.of(2026, 10, 2),
            scheduled = LocalDate.of(2026, 10, 3),
            due = LocalDate.of(2026, 10, 4))
        assertTrue(VaultRepository.add(context, draft) is WriteOutcome.Success)
        assertEquals("# Задачи\n- [ ] Подготовить письмо #task #project/demo #urgent " +
            "🛫 2026-10-02 ⏳ 2026-10-03 📅 2026-10-04\n", file.readText())
    }
    @Test fun backgroundScanRemovesExpiredDoneLineButKeepsOtherMarkdown() {
        val context = RuntimeEnvironment.getApplication()
        val active = "- [ ] Активная #task 📅 2026-10-03"
        val file = syntheticDocument(context,
            "# Задачи\n- [x] Старая #task ✅ 2020-01-01\n$active\n")
        val result = VaultRepository.scan(context)
        assertEquals("# Задачи\n$active\n", file.readText())
        assertEquals(1, result.tasks.size)
        assertEquals("Активная", result.tasks.single().task.description)
        LocalTaskWriteJournal(context, "content://synthetic.vault/tree/root").use {
            assertTrue(it.recent().isEmpty())
        }
    }
    @Test fun recurringTaskCreatesNextOccurrenceAndUndoRemovesItThroughSaf() {
        val context = RuntimeEnvironment.getApplication()
        val line = "- [ ] Еженедельно #task 🔁 every week 📅 2026-10-01"
        val file = syntheticDocument(context, "$line\n")
        val task = VaultRepository.scan(context).tasks.single()
        val row = WidgetAgenda.build(listOf(task), LocalDate.of(2026, 10, 1), 14)
            .filterIsInstance<WidgetRow.Task>().single()

        val completed = VaultRepository.complete(context, row.id)
        assertTrue(completed is WriteOutcome.Success)
        assertTrue(file.readText().contains("- [x] Еженедельно #task"))
        assertTrue(file.readText().contains("- [ ] Еженедельно #task 🔁 every week 📅 2026-10-08"))
        val activeAfter = WidgetAgenda.build(VaultRepository.indexedTasks(context),
            LocalDate.of(2026, 10, 1), 14).filterIsInstance<WidgetRow.Task>()
        assertEquals(1, activeAfter.size)

        assertTrue(VaultRepository.undo(context, (completed as WriteOutcome.Success).actionId)
            is WriteOutcome.Success)
        assertEquals("$line\n", file.readText())
        assertEquals(1, WidgetAgenda.build(VaultRepository.indexedTasks(context),
            LocalDate.of(2026, 10, 1), 14).filterIsInstance<WidgetRow.Task>().size)
    }

    @Test fun externalEditBeforeCheckboxDoesNotOverwriteSafDocument() {
        val context = RuntimeEnvironment.getApplication()
        val line = "- [ ] Первоначальная #task 📅 2026-10-02"
        val file = syntheticDocument(context, "$line\n")
        val task = VaultRepository.scan(context).tasks.single()
        val row = WidgetAgenda.build(listOf(task), LocalDate.of(2026, 10, 1), 14)
            .filterIsInstance<WidgetRow.Task>().single()
        val external = "- [ ] Изменено в Obsidian #task 📅 2026-10-02\n"
        file.writeText(external)

        assertTrue(VaultRepository.complete(context, row.id) is WriteOutcome.Conflict)
        assertEquals(external, file.readText())
        LocalTaskWriteJournal(context, "content://synthetic.vault/tree/root").use {
            assertTrue(it.recent().isEmpty())
        }
    }

    @Test fun completingOneTaskKeepsItsSiblingVisibleAndUndoRestoresOnlyTheSelectedTask() {
        val context = RuntimeEnvironment.getApplication()
        val info = ProviderInfo().apply { authority = "synthetic.vault" }
        val provider = Robolectric.buildContentProvider(SyntheticVaultProvider::class.java)
            .create(info).get()
        val firstLine = "- [ ] Первая #task 📅 2026-10-02"
        val secondLine = "- [ ] Вторая #task 📅 2026-10-02"
        val original = "$firstLine\n$secondLine\n"
        val file = File(context.filesDir, "synthetic-note.md").apply { writeText(original) }
        provider.markdown = file
        val treeUri = Uri.parse("content://synthetic.vault/tree/root")
        context.getSharedPreferences(WidgetPreferences.SETTINGS, 0).edit()
            .putString(WidgetPreferences.VAULT_URI, treeUri.toString())
            .putString(WidgetPreferences.VAULT_NAME, "Demo")
            .putString(WidgetPreferences.TASK_TAG, "#task").commit()

        val before = VaultRepository.scan(context).tasks
        assertEquals(2, before.size)
        val selected = WidgetAgenda.build(before, LocalDate.of(2026, 10, 1), 14)
            .filterIsInstance<WidgetRow.Task>().single { it.title == "Первая" }
        val completed = VaultRepository.complete(context, selected.id)
        assertTrue(completed is WriteOutcome.Success)
        assertTrue(file.readText().contains("- [x] Первая #task"))
        assertTrue(file.readText().contains("$secondLine\n"))
        val afterComplete = WidgetAgenda.build(VaultRepository.indexedTasks(context),
            LocalDate.of(2026, 10, 1), 14).filterIsInstance<WidgetRow.Task>()
        assertEquals(listOf("Вторая"), afterComplete.map { it.title })

        val undone = VaultRepository.undo(context, (completed as WriteOutcome.Success).actionId)
        assertTrue(undone is WriteOutcome.Success)
        assertEquals(original, file.readText())
        val afterUndo = WidgetAgenda.build(VaultRepository.indexedTasks(context),
            LocalDate.of(2026, 10, 1), 14).filterIsInstance<WidgetRow.Task>()
        assertEquals(listOf("Первая", "Вторая"), afterUndo.map { it.title })
    }

    @Test fun scanCompleteAndUndoModifySyntheticSafDocument() {
        val context = RuntimeEnvironment.getApplication()
        val info = ProviderInfo().apply { authority = "synthetic.vault" }
        val provider = Robolectric.buildContentProvider(SyntheticVaultProvider::class.java)
            .create(info).get()
        val line = "- [ ] Обсудить план #task 📅 2026-10-02"
        val file = File(context.filesDir, "synthetic-note.md").apply { writeText("$line\n") }
        provider.markdown = file
        val treeUri = Uri.parse("content://synthetic.vault/tree/root")
        context.getSharedPreferences(WidgetPreferences.SETTINGS, 0).edit()
            .putString(WidgetPreferences.VAULT_URI, treeUri.toString())
            .putString(WidgetPreferences.VAULT_NAME, "Demo")
            .putString(WidgetPreferences.TASK_TAG, "#task").commit()

        val first = VaultRepository.scan(context)
        assertEquals(1, first.tasks.size)
        assertEquals(1, first.filesParsed)
        val second = VaultRepository.scan(context)
        assertEquals(1, second.filesReused)
        val row = WidgetAgenda.build(first.tasks, LocalDate.of(2026, 10, 1), 14)
            .filterIsInstance<WidgetRow.Task>().single()
        val completed = VaultRepository.complete(context, row.id)
        assertTrue(completed is WriteOutcome.Success)
        assertTrue(file.readText().contains("[x]"))
        val afterComplete = LocalTaskIndex(context).load()
        assertEquals(TaskStatus.DONE, afterComplete?.files?.get("doc")?.tasks?.single()?.status)
        val visible = WidgetAgenda.build(VaultRepository.indexedTasks(context),
            LocalDate.of(2026, 10, 1), 14).filterIsInstance<WidgetRow.Task>()
        assertTrue("visible: $visible", visible.isEmpty())

        val actionId = (completed as WriteOutcome.Success).actionId
        val undone = VaultRepository.undo(context, actionId)
        assertTrue(undone is WriteOutcome.Success)
        assertEquals("$line\n", file.readText())
        assertEquals(1, VaultRepository.indexedTasks(context).size)
        assertEquals(1, VaultRepository.scan(context).tasks.size)
    }
}