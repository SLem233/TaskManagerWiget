package ru.slem.taskwidget

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class LocalTaskWriteJournal(context: Context, private val vaultKey: String) : SQLiteOpenHelper(context, "task-actions.db", null, 2), TaskWriteJournal, AutoCloseable {
    override fun close() = super.close()
    companion object {
        private const val MIGRATE_V1_TO_V2 = """ALTER TABLE actions ADD COLUMN vault_key TEXT NOT NULL DEFAULT ''"""
        private const val VISIBLE_ACTIONS = """(vault_key=? OR (vault_key='' AND status IN ('PREPARED','NEEDS_RECOVERY')))"""
        private const val CLEAR_RESOLVED = """DELETE FROM actions WHERE vault_key=? AND status IN ('DONE','UNDONE')"""
        private const val PRUNE_RESOLVED = """DELETE FROM actions WHERE id IN (
            SELECT id FROM actions WHERE vault_key=? AND status IN ('DONE','UNDONE')
            ORDER BY rowid DESC LIMIT -1 OFFSET 100
        )"""
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE actions (
            id TEXT PRIMARY KEY, vault_key TEXT NOT NULL, document_id TEXT NOT NULL, path TEXT NOT NULL,
            created_at INTEGER NOT NULL, before_line TEXT NOT NULL, after_line TEXT NOT NULL,
            inserted_line TEXT, original_line_number INTEGER NOT NULL,
            status TEXT NOT NULL, backup BLOB
        )""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) db.execSQL(MIGRATE_V1_TO_V2)
        else throw SQLiteException("Неизвестная версия журнала; записи сохранены")
    }

    override fun prepare(
        documentId: String, path: String, record: MutationRecord, before: ByteArray
    ): JournalEntry {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", id)
            put("vault_key", vaultKey)
            put("document_id", documentId)
            put("path", path)
            put("created_at", timestamp)
            put("before_line", record.beforeLine)
            put("after_line", record.afterLine)
            if (record.insertedLine == null) putNull("inserted_line") else put("inserted_line", record.insertedLine)
            put("original_line_number", record.originalLineNumber)
            put("status", JournalStatus.PREPARED.name)
            put("backup", before)
        }
        writableDatabase.insertOrThrow("actions", null, values)
        return JournalEntry(id, documentId, path, record, JournalStatus.PREPARED, timestamp)
    }

    override fun setStatus(id: String, status: JournalStatus) {
        val values = ContentValues().apply {
            put("status", status.name)
            if (status == JournalStatus.UNDONE) putNull("backup")
        }
        if (writableDatabase.update("actions", values, "id=? AND vault_key=?", arrayOf(id, vaultKey)) != 1) {
            throw SQLiteException("Действие не найдено в журнале")
        }
        if (status == JournalStatus.DONE || status == JournalStatus.UNDONE) {
            writableDatabase.execSQL(PRUNE_RESOLVED, arrayOf(vaultKey))
        }
    }

    override fun saveRecoveryBackup(id: String, before: ByteArray) {
        val values = ContentValues().apply { put("backup", before) }
        if (writableDatabase.update("actions", values,
                "id=? AND vault_key=? AND status=?",
                arrayOf(id, vaultKey, JournalStatus.DONE.name)) != 1) {
            throw SQLiteException("Не удалось сохранить копию перед Undo")
        }
    }

    override fun discard(id: String) {
        writableDatabase.delete("actions", "id=? AND vault_key=? AND status=?",
            arrayOf(id, vaultKey, JournalStatus.PREPARED.name))
    }

    override fun get(id: String): JournalEntry? = readableDatabase.query(
        "actions", null, "id=? AND vault_key=?", arrayOf(id, vaultKey), null, null, null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toEntry() else null }

    fun clearResolved() {
        writableDatabase.execSQL(CLEAR_RESOLVED, arrayOf(vaultKey))
    }

    fun discardRecovery(id: String) {
        writableDatabase.delete("actions", "id=? AND $VISIBLE_ACTIONS AND status IN (?,?)",
            arrayOf(id, vaultKey, JournalStatus.PREPARED.name, JournalStatus.NEEDS_RECOVERY.name))
    }

    fun recent(limit: Int = 100): List<JournalEntry> = readableDatabase.query(
        "actions", null, VISIBLE_ACTIONS, arrayOf(vaultKey), null, null, "created_at DESC", limit.toString()
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toEntry()) } }

    fun recoveryBackup(id: String): ByteArray? = readableDatabase.query(
        "actions", arrayOf("backup"), "id=? AND $VISIBLE_ACTIONS AND status IN (?,?)",
        arrayOf(id, vaultKey, JournalStatus.NEEDS_RECOVERY.name, JournalStatus.PREPARED.name), null, null, null
    ).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getBlob(0) else null }

    private fun Cursor.toEntry(): JournalEntry {
        fun string(name: String) = getString(getColumnIndexOrThrow(name))
        val insertedColumn = getColumnIndexOrThrow("inserted_line")
        return JournalEntry(
            id = string("id"), documentId = string("document_id"), path = string("path"),
            record = MutationRecord(
                string("before_line"), string("after_line"),
                if (isNull(insertedColumn)) null else getString(insertedColumn),
                getInt(getColumnIndexOrThrow("original_line_number"))
            ),
            status = JournalStatus.valueOf(string("status")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            legacy = string("vault_key").isEmpty()
        )
    }
}