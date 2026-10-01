package ru.slem.taskwidget

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class LocalTaskIndex(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "task-index-v1.bin"))

    fun load(): IndexSnapshot? = try {
        file.openRead().use { IndexCodec.decode(it.readBytes()) }
    } catch (_: FileNotFoundException) {
        null
    } catch (_: IOException) {
        null
    }

    fun save(snapshot: IndexSnapshot) {
        val stream = file.startWrite()
        try {
            stream.write(IndexCodec.encode(snapshot))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}