package ru.slem.taskwidget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var column: LinearLayout
    private lateinit var vaultLabel: TextView
    private lateinit var tagInput: EditText
    private lateinit var addFileInput: EditText
    private lateinit var status: TextView
    private lateinit var journalPanel: LinearLayout
    private var exportBackupId: String? = null
    private var widgetId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (widgetId < 0) {
            widgetId = AppWidgetManager.getInstance(this).getAppWidgetIds(
                ComponentName(this, TaskWidgetProvider::class.java)).firstOrNull() ?: -1
        }
        val settings = getSharedPreferences(WidgetPreferences.SETTINGS, MODE_PRIVATE)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(Color.BLACK)
        }
        column.addView(label("Задачи · настройка", 22f))
        vaultLabel = label("Vault: ${WidgetPreferences.vaultName(this).ifBlank { "не выбран" }}", 15f)
        column.addView(vaultLabel)
        column.addView(Button(this).apply {
            text = "Выбрать папку Vault"
            setOnClickListener { chooseVault() }
        })
        column.addView(label("Тег задач", 15f))
        tagInput = EditText(this).apply {
            setSingleLine(true)
            setText(settings.getString(WidgetPreferences.TASK_TAG, "#task"))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        column.addView(tagInput)
        column.addView(label("Файл для новых задач внутри Vault", 15f))
        addFileInput = EditText(this).apply {
            setSingleLine(true)
            setText(WidgetPreferences.addFile(this@MainActivity))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        column.addView(addFileInput)
        column.addView(Button(this).apply {
            text = "Сохранить файл новых задач"
            setOnClickListener {
                status.text = if (WidgetPreferences.saveAddFile(this@MainActivity,
                        addFileInput.text.toString())) "Файл новых задач сохранён"
                    else "Укажите Markdown-файл внутри Vault без .."
            }
        })
        column.addView(Button(this).apply {
            text = "Сканировать Vault"
            setOnClickListener { scanVault() }
        })
        if (widgetId >= 0) {
            column.addView(label("Размер шрифта", 15f))
            val font = SeekBar(this).apply {
                max = 10
                progress = WidgetPreferences.fontSize(this@MainActivity, widgetId).toInt() - 12
            }
            column.addView(font)
            column.addView(label("Прозрачность фона", 15f))
            val opacity = SeekBar(this).apply {
                max = 100
                progress = WidgetPreferences.opacity(this@MainActivity, widgetId)
            }
            column.addView(opacity)
            column.addView(Button(this).apply {
                text = "Сохранить вид виджета"
                setOnClickListener {
                    WidgetPreferences.saveAppearance(this@MainActivity, widgetId,
                        font.progress + 12, opacity.progress)
                    TaskWidgetProvider.refreshAll(this@MainActivity)
                    status.text = "Вид виджета сохранён"
                }
            })
        }
        status = label(WidgetPreferences.lastScanError(this) ?: "Готово к сканированию", 15f)
        column.addView(status)
        column.addView(label("Журнал действий", 18f))
        journalPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(journalPanel)
        column.addView(Button(this).apply {
            text = "Обновить журнал"
            setOnClickListener { showJournal() }
        })
        setContentView(ScrollView(this).apply {
            addView(column, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        showJournal()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1003)
        }
    }

    override fun onResume() {
        super.onResume()
        DeadlineNotifications.update(this, VaultRepository.indexedTasks(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1003) {
            DeadlineNotifications.update(this, VaultRepository.indexedTasks(this))
        }
    }
    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 12, 0, 12)
    }

    private fun chooseVault() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, PICK_VAULT)
    }

    @Deprecated("Framework result API supports API 26")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == EXPORT_BACKUP) {
            val id = exportBackupId
            exportBackupId = null
            if (id != null && data?.data != null) exportBackup(id, data.data!!)
            return
        }
        if (requestCode != PICK_VAULT) return
        val uri = data?.data ?: return
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0 ||
            flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) {
            status.text = "Папка не предоставила доступ на чтение и запись"
            return
        }
        try {
            if (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } else {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val rootId = DocumentsContract.getTreeDocumentId(uri)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(uri, rootId)
            val name = contentResolver.query(rootUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: rootId.substringAfterLast('/')
            getSharedPreferences(WidgetPreferences.SETTINGS, MODE_PRIVATE).edit()
                .putString(WidgetPreferences.VAULT_URI, uri.toString())
                .putString(WidgetPreferences.VAULT_NAME, name).apply()
            vaultLabel.text = "Vault: $name"
            scanVault()
        } catch (error: Exception) {
            status.text = "Не удалось сохранить доступ: ${error.message}"
        }
    }

    private fun scanVault() {
        if (WidgetPreferences.vaultUri(this) == null) {
            status.text = "Сначала выберите папку Vault"
            return
        }
        val tag = tagInput.text.toString().trim()
        if (!TAG_PATTERN.matches(tag)) {
            status.text = "Укажите один тег, например #task"
            return
        }
        getSharedPreferences(WidgetPreferences.SETTINGS, MODE_PRIVATE).edit()
            .putString(WidgetPreferences.TASK_TAG, tag).apply()
        status.text = "Сканирование..."
        executor.execute {
            val started = SystemClock.elapsedRealtime()
            var lastShownAt = 0L
            val message = try {
                val result = VaultRepository.scan(this) { progress ->
                    val now = SystemClock.elapsedRealtime()
                    if (lastShownAt == 0L || now - lastShownAt >= 500L) {
                        lastShownAt = now
                        val text = ScanStatusFormatter.format(progress, now - started)
                        runOnUiThread { if (!isFinishing) status.text = text }
                    }
                }
                TaskWidgetProvider.refreshAll(this)
                VaultScanJobService.ensurePeriodic(this)
                "Файлов Markdown: ${result.filesScanned}; заново: ${result.filesParsed}; " +
                    "из индекса: ${result.filesReused}; задач: ${result.tasks.size}; " +
                    "ошибок: ${result.errors.size}" +
                    result.errors.firstOrNull()?.let { "\n$it" }.orEmpty()
            } catch (error: Exception) {
                "Не удалось прочитать Vault: ${error.message}"
            }
            runOnUiThread { if (!isFinishing) status.text = message }
        }
    }

    private fun showJournal() {
        journalPanel.removeAllViews()
        LocalTaskWriteJournal(this, WidgetPreferences.vaultUri(this).orEmpty()).use { journal ->
            val entries = journal.recent(20)
            if (entries.isEmpty()) journalPanel.addView(label("Пока пусто", 14f))
            entries.forEach { entry ->
                val legacy = if (entry.legacy) "Старый журнал · Vault неизвестен · " else ""
                journalPanel.addView(label("$legacy${entry.path} · ${entry.status}\n${entry.record.beforeLine}", 13f))
                when (entry.status) {
                    JournalStatus.DONE -> journalPanel.addView(Button(this).apply {
                        text = "Отменить"
                        setOnClickListener { undo(entry.id) }
                    })
                    JournalStatus.PREPARED, JournalStatus.NEEDS_RECOVERY -> journalPanel.addView(Button(this).apply {
                        text = "Экспортировать резервную копию"
                        setOnClickListener {
                            exportBackupId = entry.id
                            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                type = "text/markdown"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "task-recovery-${entry.id}.md")
                            }, EXPORT_BACKUP)
                        }
                    })
                    else -> Unit
                }
                if (entry.status == JournalStatus.PREPARED || entry.status == JournalStatus.NEEDS_RECOVERY) {
                    journalPanel.addView(Button(this).apply {
                        text = "Удалить резервную запись"
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setMessage("Удаляйте только после проверки заметки и копии. Отменить удаление нельзя.")
                                .setNegativeButton("Отмена", null)
                                .setPositiveButton("Удалить") { _, _ ->
                                    LocalTaskWriteJournal(this@MainActivity,
                                        WidgetPreferences.vaultUri(this@MainActivity).orEmpty()).use {
                                        it.discardRecovery(entry.id)
                                    }
                                    showJournal()
                                }.show()
                        }
                    })
                }
            }
            if (entries.isNotEmpty()) {
                journalPanel.addView(Button(this).apply {
                    text = "Очистить завершённую историю"
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setMessage("Удалить историю и возможность Undo для завершённых действий?")
                            .setNegativeButton("Отмена", null)
                            .setPositiveButton("Очистить") { _, _ ->
                                LocalTaskWriteJournal(this@MainActivity,
                                    WidgetPreferences.vaultUri(this@MainActivity).orEmpty()).use {
                                    it.clearResolved()
                                }
                                showJournal()
                            }.show()
                    }
                })
            }
        }
    }

    private fun undo(actionId: String) {
        status.text = "Отмена..."
        executor.execute {
            val result = try { VaultRepository.undo(this, actionId) }
                catch (error: Exception) { WriteOutcome.Failure("Отмена: ${error.message}") }
            TaskWidgetProvider.refreshAll(this)
            VaultScanJobService.scheduleNow(this)
            runOnUiThread {
                status.text = when (result) {
                    is WriteOutcome.Success -> "Действие отменено"
                    is WriteOutcome.Conflict -> result.reason
                    is WriteOutcome.Unsupported -> result.reason
                    is WriteOutcome.Failure -> result.reason
                }
                showJournal()
            }
        }
    }

    private fun exportBackup(id: String, uri: Uri) {
        try {
            val bytes = LocalTaskWriteJournal(this, WidgetPreferences.vaultUri(this).orEmpty()).use { it.recoveryBackup(id) }
                ?: error("Резервная копия не найдена")
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("Не удалось открыть файл")
            status.text = "Резервная копия сохранена; проверьте заметку перед восстановлением"
        } catch (error: Exception) {
            status.text = "Экспорт не удался: ${error.message}"
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val PICK_VAULT = 1001
        private const val EXPORT_BACKUP = 1002
        private val TAG_PATTERN = Regex("""#[\p{L}\p{N}_/\-]+""")
    }
}