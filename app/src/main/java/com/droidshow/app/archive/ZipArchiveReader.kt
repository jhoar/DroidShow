package com.droidshow.app.archive

import android.content.Context
import android.net.Uri
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipArchiveReader(
    private val context: Context,
    private val archiveUri: Uri
) : ArchiveReader {

    private val imageEntries by lazy {
        buildList {
            openArchiveStream().use { input ->
                ZipInputStream(input).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    var index = 0
                    while (entry != null) {
                        val currentEntry = entry
                        val name = currentEntry.name
                        if (!currentEntry.isDirectory && ArchiveEntrySupport.isImageEntry(name)) {
                            add(
                                ArchiveEntryRef(
                                    archiveUri = archiveUri,
                                    entryPath = name,
                                    compressedSize = currentEntry.compressedSize,
                                    uncompressedSize = currentEntry.size,
                                    index = index
                                )
                            )
                        }
                        index++
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        }.sortedWith(compareBy(ArchiveEntrySupport.naturalPathComparator) { it.entryPath })
    }

    override fun listImageEntries(): List<ArchiveEntryRef> = imageEntries

    override fun openEntryStream(entry: ArchiveEntryRef): InputStream {
        require(entry.archiveUri == archiveUri) {
            "Archive entry does not belong to this archive reader."
        }

        val baseStream = openArchiveStream()
        val zipInputStream = ZipInputStream(baseStream)
        var current = zipInputStream.nextEntry
        var currentIndex = 0
        while (current != null) {
            if (!current.isDirectory && currentIndex == entry.index && current.name == entry.entryPath) {
                return object : FilterInputStream(zipInputStream) {
                    override fun close() {
                        super.close()
                        baseStream.close()
                    }
                }
            }
            zipInputStream.closeEntry()
            current = zipInputStream.nextEntry
            currentIndex++
        }

        zipInputStream.close()
        throw IllegalArgumentException("ZIP entry not found: ${entry.entryPath}")
    }

    override fun close() = Unit

    private fun openArchiveStream(): InputStream {
        return requireNotNull(context.contentResolver.openInputStream(archiveUri)) {
            "Unable to open archive URI: $archiveUri"
        }
    }
}
