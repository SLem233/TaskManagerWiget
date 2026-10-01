package ru.slem.taskwidget

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

object IndexCodec {
    private const val MAGIC = 0x544D5749
    private const val VERSION = 1
    private const val MAX_FILES = 200_000
    private const val MAX_TASKS_PER_FILE = 100_000
    private const val MAX_STRING_BYTES = 2_000_000

    fun encode(snapshot: IndexSnapshot): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeString(snapshot.vaultKey)
            output.writeString(snapshot.requiredTag)
            output.writeInt(snapshot.files.size)
            for (file in snapshot.files.values) {
                output.writeString(file.id)
                output.writeString(file.path)
                output.writeLong(file.lastModified)
                output.writeLong(file.size)
                output.writeInt(file.tasks.size)
                for (task in file.tasks) {
                    output.writeInt(task.lineNumber)
                    output.writeString(task.originalLine)
                }
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): IndexSnapshot? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
            val vaultKey = input.readString()
            val tag = input.readString()
            val fileCount = input.readCount(MAX_FILES)
            val files = linkedMapOf<String, CachedFile>()
            repeat(fileCount) {
                val id = input.readString()
                val path = input.readString()
                val modified = input.readLong()
                val size = input.readLong()
                val taskCount = input.readCount(MAX_TASKS_PER_FILE)
                val tasks = buildList {
                    repeat(taskCount) {
                        val lineNumber = input.readInt()
                        val line = input.readString()
                        val task = TaskParser.parseLine(line, lineNumber, tag) ?: return null
                        add(task)
                    }
                }
                if (files.put(id, CachedFile(id, path, modified, size, tasks)) != null) return null
            }
            IndexSnapshot(vaultKey, tag, files)
        }
    } catch (_: Exception) {
        null
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readCount(MAX_STRING_BYTES)
        val bytes = ByteArray(size)
        readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun DataInputStream.readCount(maximum: Int): Int = readInt().also {
        require(it in 0..maximum)
    }
}