package showlio.app.archive

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class ArchiveReaderRandomAccessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `zip reader can open image entries repeatedly in random order`() {
        val imagePayloads = linkedMapOf(
            "images/001.png" to "zip-image-one".encodeToByteArray(),
            "images/010.jpg" to "zip-image-two".encodeToByteArray(),
            "images/002.webp" to "zip-image-three".encodeToByteArray()
        )
        val zipFile = createZipArchive(imagePayloads + ("docs/readme.txt" to "ignore".encodeToByteArray()))

        ZipArchiveReader(context, Uri.fromFile(zipFile)).use { reader ->
            val entries = reader.listImageEntries()
            assertEquals(imagePayloads.keys.sorted(), entries.map { it.entryPath }.sorted())

            val randomOrder = entries.shuffled(Random(42)) + entries.shuffled(Random(314))
            randomOrder.forEach { ref ->
                val bytes = reader.openEntryStream(ref).use { it.readBytes() }
                assertTrue(bytes.contentEquals(imagePayloads.getValue(ref.entryPath)))
            }
        }
    }

    @Test
    fun `7z reader can open image entries repeatedly in random order`() {
        val imagePayloads = linkedMapOf(
            "images/100.png" to "sevenz-image-one".encodeToByteArray(),
            "images/020.jpg" to "sevenz-image-two".encodeToByteArray(),
            "images/003.avif" to "sevenz-image-three".encodeToByteArray()
        )
        val sevenZFile = createSevenZArchive(imagePayloads + ("notes/skip.txt" to "ignore".encodeToByteArray()))

        SevenZArchiveReader(context, Uri.fromFile(sevenZFile)).use { reader ->
            val entries = reader.listImageEntries()
            assertEquals(imagePayloads.keys.sorted(), entries.map { it.entryPath }.sorted())

            val randomOrder = entries.shuffled(Random(7)) + entries.shuffled(Random(77))
            randomOrder.forEach { ref ->
                val bytes = reader.openEntryStream(ref).use { it.readBytes() }
                assertTrue(bytes.contentEquals(imagePayloads.getValue(ref.entryPath)))
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `7z reader rejects entry access after close`() {
        val sevenZFile = createSevenZArchive(mapOf("images/locked.png" to "locked".encodeToByteArray()))
        val reader = SevenZArchiveReader(context, Uri.fromFile(sevenZFile))
        val entry = reader.listImageEntries().first()

        reader.close()
        reader.openEntryStream(entry)
    }

    private fun createZipArchive(entries: Map<String, ByteArray>): File {
        val file = File.createTempFile("zip-random-access", ".zip", context.cacheDir)
        ZipOutputStream(file.outputStream().buffered()).use { output ->
            entries.forEach { (name, payload) ->
                output.putNextEntry(ZipEntry(name))
                output.write(payload)
                output.closeEntry()
            }
        }
        return file
    }

    private fun createSevenZArchive(entries: Map<String, ByteArray>): File {
        val file = File.createTempFile("sevenz-random-access", ".7z", context.cacheDir)
        SevenZOutputFile(file).use { output ->
            entries.forEach { (name, payload) ->
                val entry: SevenZArchiveEntry = output.createArchiveEntry(file, name)
                entry.size = payload.size.toLong()
                output.putArchiveEntry(entry)
                output.write(payload)
                output.closeArchiveEntry()
            }
            output.finish()
        }
        return file
    }
}
