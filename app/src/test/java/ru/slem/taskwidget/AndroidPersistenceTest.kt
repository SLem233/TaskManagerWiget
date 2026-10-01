package ru.slem.taskwidget

import android.content.Context
import android.util.AtomicFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 36], shadows = [WindowsAtomicFileShadow::class])
class AndroidPersistenceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Test fun atomicFileReplacesExistingBase() {
        val base = java.io.File(context.filesDir, "atomic-replacement.bin")
        val file = AtomicFile(base)
        file.startWrite().also { it.write(byteArrayOf(1)); file.finishWrite(it) }
        file.startWrite().also { it.write(byteArrayOf(2)); file.finishWrite(it) }
        assertArrayEquals(byteArrayOf(2), file.openRead().use { it.readBytes() })
        assertFalse(java.io.File("$base.new").exists())
    }

    @Test fun settingsScreenOpensWithEmptyJournal() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(activity)
        assertEquals("#task", WidgetPreferences.tag(activity))
        activity.finish()
    }

    @Test fun preferencesPersistAppearanceAndRejectWrongActionToken() {
        assertNull(WidgetPreferences.vaultUri(context))
        val token = WidgetPreferences.token(context)
        assertTrue(WidgetPreferences.validToken(context, token))
        assertFalse(WidgetPreferences.validToken(context, "forged"))
        assertFalse(WidgetPreferences.validToken(context, null))
        WidgetPreferences.saveAppearance(context, 9, 19, 43)
        assertEquals(19f, WidgetPreferences.fontSize(context, 9))
        assertEquals(43, WidgetPreferences.opacity(context, 9))
        assertEquals(15f, WidgetPreferences.fontSize(context, 10))
    }

    @Test fun sqliteJournalRetainsRecoveryAndScopesActionsByVault() {
        val line = "- [ ] Задача #task"
        val record = MutationRecord(line, "- [x] Задача #task ✅ 2026-10-01", null, 1)
        LocalTaskWriteJournal(context, "vault-a").use { journal ->
            val entry = journal.prepare("doc", "Note.md", record, "$line\n".toByteArray())
            assertEquals(JournalStatus.PREPARED, journal.get(entry.id)?.status)
            journal.setStatus(entry.id, JournalStatus.DONE)
            assertEquals(1, journal.recent().size)
            journal.saveRecoveryBackup(entry.id, "newer content".toByteArray())
            journal.setStatus(entry.id, JournalStatus.NEEDS_RECOVERY)
            assertEquals("newer content", String(requireNotNull(journal.recoveryBackup(entry.id))))
            journal.clearResolved()
            assertEquals(JournalStatus.NEEDS_RECOVERY, journal.get(entry.id)?.status)
            LocalTaskWriteJournal(context, "vault-b").use { other ->
                assertNull(other.get(entry.id))
                assertTrue(other.recent().isEmpty())
            }
            journal.discardRecovery(entry.id)
            assertNull(journal.get(entry.id))
        }
    }

    @Test fun localIndexRoundTripsAndRejectsDamagedBytes() {
        val index = LocalTaskIndex(context)
        assertNull(index.load())
        val snapshot = IndexSnapshot("vault", "#task", emptyMap())
        index.save(snapshot)
        assertEquals(snapshot, index.load())
        val parsed = requireNotNull(TaskParser.parseLine("- [ ] Задача #task", 1, "#task"))
        val populated = snapshot.copy(files = mapOf("doc" to
            CachedFile("doc", "Note.md", 1, 10, listOf(parsed))))
        index.save(populated)
        val disk = context.filesDir.listFiles().orEmpty().filter { it.name.startsWith("task-index-v1.bin") }
            .joinToString { "${it.name}:${it.length()}:${IndexCodec.decode(it.readBytes())?.files?.size}" }
        assertEquals("files=$disk", populated, index.load())
        index.save(snapshot)
        assertEquals("Repeated AtomicFile save must replace older index", snapshot, index.load())
        context.openFileOutput("task-index-v1.bin", Context.MODE_PRIVATE).use { it.write(byteArrayOf(1, 2)) }
        assertNull(index.load())
    }
}