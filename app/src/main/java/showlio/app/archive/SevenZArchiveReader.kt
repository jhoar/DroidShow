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

    private val archiveFile = ArchiveTempCache(context.applicationContext).getOrCreate(archiveUri, ".7z")

    private data class SevenZEntryMetadata(
        val path: String,
        val index: Int,
        val uncompressedSize: Long
    )

    private val metadataEntries by lazy {
        val discovered = mutableListOf<SevenZEntryMetadata>()
        SevenZFile.builder().setFile(archiveFile).get().use { reader ->
            var index = 0
            var entry = reader.nextEntry
            while (entry != null) {
                if (entry.isImageFile()) {
                    discovered += SevenZEntryMetadata(
                        path = entry.requireName(),
                        index = index,
                        uncompressedSize = entry.size
                    )
                }
                index++
                entry = reader.nextEntry
            }
        }
        discovered
    }

    private var sevenZFile: SevenZFile? = null
    private var isClosed = false
    private var metadataByIndexAndPath: Map<Pair<Int, String>, SevenZEntryMetadata>? = null
    private var sevenZEntriesByIndexAndPath: Map<Pair<Int, String>, SevenZArchiveEntry>? = null

    private val imageEntries by lazy {
        metadataEntries
            .map {
                ArchiveEntryRef(
                    archiveUri = archiveUri,
                    entryPath = it.path,
                    compressedSize = -1L,
                    uncompressedSize = it.uncompressedSize,
                    index = it.index
                )
            }
            .sortedWith(compareBy(ArchiveEntrySupport.naturalPathComparator) { it.entryPath })
    }

    override fun listImageEntries(): List<ArchiveEntryRef> = imageEntries

    override fun openEntryStream(entry: ArchiveEntryRef): InputStream {
        require(entry.archiveUri == archiveUri) {
            "Archive entry does not belong to this archive reader."
        }

        check(!isClosed) { "Archive reader has been closed." }

        val key = entry.index to entry.entryPath
        val metadata = requireMetadataByIndexAndPath()[key]
            ?: throw IllegalArgumentException("7z entry not found: ${entry.entryPath}")
        val sevenZEntry = requireSevenZEntriesByIndexAndPath()[metadata.index to metadata.path]
            ?: throw IllegalArgumentException("7z entry lookup missing: ${entry.entryPath}")

        return requireSevenZFile().getInputStream(sevenZEntry)
    }

    override fun close() {
        isClosed = true
        sevenZFile?.close()
        sevenZFile = null
        metadataByIndexAndPath = null
        sevenZEntriesByIndexAndPath = null
    }

    @Synchronized
    private fun requireSevenZFile(): SevenZFile {
        check(!isClosed) { "Archive reader has been closed." }

        val existing = sevenZFile
        if (existing != null) {
            return existing
        }

        return SevenZFile.builder()
            .setFile(archiveFile)
            .get()
            .also { sevenZFile = it }
    }

    private fun requireMetadataByIndexAndPath(): Map<Pair<Int, String>, SevenZEntryMetadata> {
        val existing = metadataByIndexAndPath
        if (existing != null) {
            return existing
        }

        return metadataEntries
            .associateBy { it.index to it.path }
            .also { metadataByIndexAndPath = it }
    }

    private fun requireSevenZEntriesByIndexAndPath(): Map<Pair<Int, String>, SevenZArchiveEntry> {
        val existing = sevenZEntriesByIndexAndPath
        if (existing != null) {
            return existing
        }

        val entries = mutableMapOf<Pair<Int, String>, SevenZArchiveEntry>()
        val reader = requireSevenZFile()
        var index = 0
        var entry = reader.nextEntry
        while (entry != null) {
            val name = entry.name
            if (!entry.isDirectory && name != null && ArchiveEntrySupport.isImageEntry(name)) {
                entries[index to name] = entry
            }
            index++
            entry = reader.nextEntry
        }

        return entries.also { sevenZEntriesByIndexAndPath = it }
    }

    private fun SevenZArchiveEntry.isImageFile(): Boolean =
        !isDirectory && ArchiveEntrySupport.isImageEntry(name ?: "")

    private fun SevenZArchiveEntry.requireName(): String =
        name ?: throw IOException("Encountered 7z entry without a name.")
}
