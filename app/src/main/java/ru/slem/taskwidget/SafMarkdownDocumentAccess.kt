package ru.slem.taskwidget

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

class SafMarkdownDocumentAccess(
    private val resolver: ContentResolver,
    private val treeUri: Uri
) : MarkdownDocumentAccess {
    private fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    override fun read(id: String): ByteArray =
        resolver.openInputStream(documentUri(id))?.use { it.readBytes() }
            ?: throw IOException("Не удалось открыть файл")

    override fun write(id: String, bytes: ByteArray) {
        val stream = resolver.openOutputStream(documentUri(id), "wt")
            ?: throw IOException("Поставщик файлов отказал в записи")
        stream.use {
            it.write(bytes)
            it.flush()
        }
    }
}