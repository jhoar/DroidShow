package showlio.app.archive

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

    private data class SevenZEntryMetadata(
        val ref: ArchiveEntryRef,
        val entry: SevenZArchiveEntry
    )

    private var sevenZFile: SevenZFile? = null
    private var isClosed = false

    private val indexedEntries by lazy {
        val discovered = mutableListOf<SevenZEntryMetadata>()
        val reader = requireSevenZFile()
        var index = 0
        var entry = reader.nextEntry
        while (entry != null) {
            if (entry.isImageFile()) {
                discovered += SevenZEntryMetadata(
                    ref = entry.toRef(index),
                    entry = entry
                )
            }
            index++
            entry = reader.nextEntry
        }
        discovered
    }

    private val metadataByIndexAndPath by lazy {
        indexedEntries.associateBy { it.ref.index to it.ref.entryPath }
    }

    private val imageEntries by lazy {
        indexedEntries
            .map { it.ref }
            .sortedWith(compareBy(ArchiveEntrySupport.naturalPathComparator) { it.entryPath })
    }

    override fun listImageEntries(): List<ArchiveEntryRef> = imageEntries

    override fun openEntryStream(entry: ArchiveEntryRef): InputStream {
        require(entry.archiveUri == archiveUri) {
            "Archive entry does not belong to this archive reader."
        }

        check(!isClosed) { "Archive reader has been closed." }

        val metadata = metadataByIndexAndPath[entry.index to entry.entryPath]
            ?: throw IllegalArgumentException("7z entry not found: ${entry.entryPath}")

        return requireSevenZFile().getInputStream(metadata.entry)
    }

    override fun close() {
        isClosed = true
        sevenZFile?.close()
        sevenZFile = null
        tempArchive.cleanup()
    }

    @Synchronized
    private fun requireSevenZFile(): SevenZFile {
        check(!isClosed) { "Archive reader has been closed." }

        val existing = sevenZFile
        if (existing != null) {
            return existing
        }

        return SevenZFile.builder()
            .setFile(tempArchive.file)
            .get()
            .also { sevenZFile = it }
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
