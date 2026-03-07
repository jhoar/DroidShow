package com.droidshow.app.archive

import android.content.Context
import android.net.Uri
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.IOException
import java.io.InputStream

class SevenZArchiveReader(
    context: Context,
    private val archiveUri: Uri
) : ArchiveReader {

    private val tempArchive = TempArchiveFile(context, archiveUri, ".7z")

    private val imageEntries by lazy {
        val discovered = mutableListOf<ArchiveEntryRef>()
        SevenZFile(tempArchive.file).use { sevenZFile ->
            var index = 0
            var entry = sevenZFile.nextEntry
            while (entry != null) {
                if (entry.isImageFile()) {
                    discovered += entry.toRef(index)
                }
                index++
                entry = sevenZFile.nextEntry
            }
        }
        discovered.sortedWith(compareBy(ArchiveEntrySupport.naturalPathComparator) { it.entryPath })
    }

    override fun listImageEntries(): List<ArchiveEntryRef> = imageEntries

    override fun openEntryStream(entry: ArchiveEntryRef): InputStream {
        require(entry.archiveUri == archiveUri) {
            "Archive entry does not belong to this archive reader."
        }

        val sevenZFile = SevenZFile(tempArchive.file)
        var currentIndex = 0
        var current = sevenZFile.nextEntry
        while (current != null) {
            if (currentIndex == entry.index && current.name == entry.entryPath) {
                return object : InputStream() {
                    override fun read(): Int {
                        val oneByte = ByteArray(1)
                        val read = sevenZFile.read(oneByte)
                        return if (read <= 0) -1 else oneByte[0].toInt() and 0xFF
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int = sevenZFile.read(b, off, len)

                    override fun close() {
                        sevenZFile.close()
                    }
                }
            }
            currentIndex++
            current = sevenZFile.nextEntry
        }

        sevenZFile.close()
        throw IllegalArgumentException("7z entry not found: ${entry.entryPath}")
    }

    override fun close() {
        tempArchive.cleanup()
    }

    private fun SevenZArchiveEntry.isImageFile(): Boolean =
        !isDirectory && ArchiveEntrySupport.isImageEntry(name ?: "")

    private fun SevenZArchiveEntry.toRef(index: Int): ArchiveEntryRef {
        val name = name ?: throw IOException("Encountered 7z entry without a name.")
        return ArchiveEntryRef(
            archiveUri = archiveUri,
            entryPath = name,
            compressedSize = -1L,
            uncompressedSize = size,
            index = index
        )
    }
}
