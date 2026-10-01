package ru.slem.taskwidget

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

class SafVaultTree(private val resolver: ContentResolver, private val treeUri: Uri) : VaultTree {
    val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)

    override fun list(parentId: String): List<VaultEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val cursor = resolver.query(childrenUri, columns, null, null, null)
            ?: throw IOException("Поставщик файлов не вернул список")
        return cursor.use {
            val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val typeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modifiedColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            buildList {
                while (it.moveToNext()) {
                    val id = it.getString(idColumn) ?: continue
                    val name = it.getString(nameColumn) ?: continue
                    val type = it.getString(typeColumn)
                    val modified = if (modifiedColumn >= 0 && !it.isNull(modifiedColumn)) {
                        it.getLong(modifiedColumn)
                    } else 0L
                    val size = if (sizeColumn >= 0 && !it.isNull(sizeColumn)) it.getLong(sizeColumn) else -1L
                    add(VaultEntry(id, name, type == DocumentsContract.Document.MIME_TYPE_DIR, modified, size))
                }
            }
        }
    }

    override fun read(id: String): String {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
        return resolver.openInputStream(documentUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IOException("Не удалось открыть Markdown")
    }
}