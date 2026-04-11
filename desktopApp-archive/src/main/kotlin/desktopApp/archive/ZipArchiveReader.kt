package desktopApp.archive

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.InputStream
import java.nio.file.Path
import kotlin.collections.LinkedHashMap

class ZipArchiveReader(
    private val archivePath: Path
) : DesktopArchiveReader {

    private data class ZipEntryMetadata(
        val path: String,
        val index: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long
    )

    private val metadataEntries by lazy {
        val discovered = mutableListOf<ZipEntryMetadata>()
        val zipFile = requireZipFile()
        val entries = zipFile.entries
        var index = 0
        while (entries.hasMoreElements()) {
            val currentEntry = entries.nextElement()
            val name = currentEntry.name
            if (!currentEntry.isDirectory && ArchiveEntrySupport.isImageEntry(name)) {
                discovered += ZipEntryMetadata(
                    path = name,
                    index = index,
                    compressedSize = currentEntry.compressedSize,
                    uncompressedSize = currentEntry.size,
                    localHeaderOffset = currentEntry.localHeaderOffset
                )
            }
            index++
        }
        discovered
    }

    private var metadataByIndexAndPath: Map<Pair<Int, String>, ZipEntryMetadata>? = null
    private var zipEntriesByOffset: Map<Long, ZipArchiveEntry>? = null

    private val imageEntries by lazy {
        metadataEntries
            .map {
                DesktopArchiveEntryRef(
                    archivePath = archivePath,
                    entryPath = it.path,
                    compressedSize = it.compressedSize,
                    uncompressedSize = it.uncompressedSize,
                    index = it.index
                )
            }
            .sortedWith(compareBy(ArchiveEntrySupport.naturalPathComparator) { it.entryPath })
    }

    private var zipFile: ZipFile? = null

    override fun listImageEntries(): List<DesktopArchiveEntryRef> = imageEntries

    override fun openEntryStream(entry: DesktopArchiveEntryRef): InputStream {
        require(entry.archivePath == archivePath) {
            "Archive entry does not belong to this archive reader."
        }

        val metadata = requireMetadataByIndexAndPath()[entry.index to entry.entryPath]
            ?: throw IllegalArgumentException("ZIP entry not found: ${entry.entryPath}")

        val zipEntry = requireZipEntriesByOffset()[metadata.localHeaderOffset]
            ?: throw IllegalArgumentException("ZIP entry offset not found: ${entry.entryPath}")

        return requireZipFile().getInputStream(zipEntry)
    }

    override fun close() {
        zipFile?.close()
        zipFile = null
        metadataByIndexAndPath = null
        zipEntriesByOffset = null
    }

    @Synchronized
    private fun requireZipFile(): ZipFile {
        val existing = zipFile
        if (existing != null) {
            return existing
        }

        return ZipFile.builder()
            .setPath(archivePath)
            .get()
            .also { zipFile = it }
    }

    private fun requireMetadataByIndexAndPath(): Map<Pair<Int, String>, ZipEntryMetadata> {
        val existing = metadataByIndexAndPath
        if (existing != null) {
            return existing
        }

        return metadataEntries
            .associateBy { it.index to it.path }
            .also { metadataByIndexAndPath = it }
    }

    private fun requireZipEntriesByOffset(): Map<Long, ZipArchiveEntry> {
        val existing = zipEntriesByOffset
        if (existing != null) {
            return existing
        }

        val entries = LinkedHashMap<Long, ZipArchiveEntry>()
        val iterator = requireZipFile().entriesInPhysicalOrder
        while (iterator.hasMoreElements()) {
            val zipEntry = iterator.nextElement()
            entries[zipEntry.localHeaderOffset] = zipEntry
        }

        return entries.also { zipEntriesByOffset = it }
    }
}
