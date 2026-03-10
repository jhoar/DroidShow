package showlio.app.archive

import android.content.Context
import android.net.Uri
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.InputStream

class ZipArchiveReader(
    context: Context,
    private val archiveUri: Uri
) : ArchiveReader {

    private val archiveFile = ArchiveTempCache(context.applicationContext).getOrCreate(archiveUri, ".zip")

    private data class ZipEntryMetadata(
        val ref: ArchiveEntryRef,
        val entry: ZipArchiveEntry,
        val localHeaderOffset: Long
    )

    private val indexedEntries by lazy {
        val discovered = mutableListOf<ZipEntryMetadata>()
        val zipFile = requireZipFile()
        val entries = zipFile.entries
        var index = 0
        while (entries.hasMoreElements()) {
            val currentEntry = entries.nextElement()
            val name = currentEntry.name
            if (!currentEntry.isDirectory && ArchiveEntrySupport.isImageEntry(name)) {
                discovered += ZipEntryMetadata(
                    ref = ArchiveEntryRef(
                        archiveUri = archiveUri,
                        entryPath = name,
                        compressedSize = currentEntry.compressedSize,
                        uncompressedSize = currentEntry.size,
                        index = index
                    ),
                    entry = currentEntry,
                    localHeaderOffset = currentEntry.localHeaderOffset
                )
            }
            index++
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

    private var zipFile: ZipFile? = null

    override fun listImageEntries(): List<ArchiveEntryRef> = imageEntries

    override fun openEntryStream(entry: ArchiveEntryRef): InputStream {
        require(entry.archiveUri == archiveUri) {
            "Archive entry does not belong to this archive reader."
        }

        val metadata = metadataByIndexAndPath[entry.index to entry.entryPath]
            ?: throw IllegalArgumentException("ZIP entry not found: ${entry.entryPath}")

        return requireZipFile().getInputStream(metadata.entry)
    }

    override fun close() {
        zipFile?.close()
        zipFile = null
    }

    @Synchronized
    private fun requireZipFile(): ZipFile {
        val existing = zipFile
        if (existing != null) {
            return existing
        }

        return ZipFile.builder()
            .setFile(archiveFile)
            .get()
            .also { zipFile = it }
    }
}
