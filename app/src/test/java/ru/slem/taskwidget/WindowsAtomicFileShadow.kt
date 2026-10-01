package ru.slem.taskwidget

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject

/** Mirrors Android's replace-on-rename semantics on the Windows Robolectric host. */
@Implements(value = AtomicFile::class, minSdk = 30)
class WindowsAtomicFileShadow {
    @RealObject private lateinit var atomicFile: AtomicFile

    @Implementation
    fun finishWrite(stream: FileOutputStream?) {
        if (stream == null) return
        stream.fd.sync()
        stream.close()
        val base = atomicFile.baseFile.toPath()
        Files.move(File("$base.new").toPath(), base, ATOMIC_MOVE, REPLACE_EXISTING)
    }
}