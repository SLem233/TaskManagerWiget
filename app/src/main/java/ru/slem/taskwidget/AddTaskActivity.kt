package ru.slem.taskwidget

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.util.concurrent.Executors

class AddTaskActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private var start: LocalDate? = null
    private var scheduled: LocalDate? = null
    private var due: LocalDate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)
        start = savedInstanceState?.getString("start")?.let(LocalDate::parse)
        scheduled = savedInstanceState?.getString("scheduled")?.let(LocalDate::parse)
        due = savedInstanceState?.getString("due")?.let(LocalDate::parse)
        findViewById<TextView>(R.id.add_target).text = "Файл: ${WidgetPreferences.addFile(this)}"
        if (savedInstanceState == null) {
            findViewById<EditText>(R.id.add_tags).setText(WidgetPreferences.tag(this))
        }
        bindDate(R.id.pick_start, R.id.clear_start, "Старт", { start }) { start = it }
        bindDate(R.id.pick_scheduled, R.id.clear_scheduled, "Приступить", { scheduled }) {
            scheduled = it
        }
        bindDate(R.id.pick_due, R.id.clear_due, "Дедлайн", { due }) { due = it }
        findViewById<Button>(R.id.cancel_task).setOnClickListener { finish() }
        findViewById<Button>(R.id.save_task).setOnClickListener { save() }
    }

    private fun bindDate(
        pickId: Int, clearId: Int, label: String,
        current: () -> LocalDate?, change: (LocalDate?) -> Unit
    ) {
        val pick = findViewById<Button>(pickId)
        fun showValue() { pick.text = "$label: ${current() ?: "без даты"}" }
        showValue()
        pick.setOnClickListener {
            val day = current() ?: LocalDate.now()
            DatePickerDialog(this, { _, year, month, date ->
                change(LocalDate.of(year, month + 1, date))
                showValue()
            }, day.year, day.monthValue - 1, day.dayOfMonth).show()
        }
        findViewById<Button>(clearId).setOnClickListener {
            change(null)
            showValue()
        }
    }

    private fun save() {
        val draft = TaskDraft(
            description = findViewById<EditText>(R.id.add_description).text.toString(),
            tags = findViewById<EditText>(R.id.add_tags).text.toString(),
            start = start, scheduled = scheduled, due = due
        )
        val status = findViewById<TextView>(R.id.add_status)
        if (draft.markdownLine(WidgetPreferences.tag(this)) == null) {
            status.text = "Укажите название в одной строке и теги вида #task через пробел"
            return
        }
        val saveButton = findViewById<Button>(R.id.save_task)
        saveButton.isEnabled = false
        status.text = "Сохраняю..."
        val app = applicationContext
        executor.execute {
            val outcome = try { VaultRepository.add(app, draft) }
                catch (error: Exception) { WriteOutcome.Failure("Добавление: ${error.message}") }
            if (outcome is WriteOutcome.Success) {
                VaultScanJobService.scheduleNow(app)
                TaskWidgetProvider.refreshAll(app)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                saveButton.isEnabled = true
                if (outcome is WriteOutcome.Success) {
                    Toast.makeText(this, "Задача добавлена", Toast.LENGTH_SHORT).show()
                    finish()
                } else status.text = when (outcome) {
                    is WriteOutcome.Conflict -> outcome.reason
                    is WriteOutcome.Unsupported -> outcome.reason
                    is WriteOutcome.Failure -> outcome.reason
                    is WriteOutcome.Success -> ""
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        start?.let { outState.putString("start", it.toString()) }
        scheduled?.let { outState.putString("scheduled", it.toString()) }
        due?.let { outState.putString("due", it.toString()) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}