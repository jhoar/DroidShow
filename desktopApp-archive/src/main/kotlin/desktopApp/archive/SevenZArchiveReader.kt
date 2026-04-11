package desktopApp.archive

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path

class SevenZArchiveReader(
    private val archivePath: Path
) : DesktopArchiveReader {

    private data class SevenZEntryMetadata(
        val path: String,
        val index: Int,
        val uncompressedSize: Long
    )

    private val metadataEntries by lazy {
        val discovered = mutableListOf<SevenZEntryMetadata>()
        SevenZFile.builder().setPath(archivePath).get().use { reader ->
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

    private var isClosed = false
    private var metadataByIndexAndPath: Map<Pair<Int, String>, SevenZEntryMetadata>? = null

    private val imageEntries by lazy {
        metadataEntries
            .map {
                DesktopArchiveEntryRef(
                    archivePath = archivePath,
                    entryPath = it.path,
                    compressedSize = -1L,
                    uncompressedSize = it.uncompressedSize,
                    index = it.index
                )
            }
            .sortedWith(compareBy(ArchiveEntrySupport.naturalPathComparator) { it.entryPath })
    }

    override fun listImageEntries(): List<DesktopArchiveEntryRef> = imageEntries

    override fun openEntryStream(entry: DesktopArchiveEntryRef): InputStream {
        require(entry.archivePath == archivePath) {
            "Archive entry does not belong to this archive reader."
        }

        check(!isClosed) { "Archive reader has been closed." }

        val key = entry.index to entry.entryPath
        val metadata = requireMetadataByIndexAndPath()[key]
            ?: throw IllegalArgumentException("7z entry not found: ${entry.entryPath}")
        val archive = SevenZFile.builder()
            .setPath(archivePath)
            .get()
        val sevenZEntry = archive.readEntryAtIndex(metadata.index)
            ?: run {
                archive.close()
                throw IllegalArgumentException("7z entry index out of range: ${metadata.index}")
            }

        if (sevenZEntry.name != metadata.path) {
            archive.close()
            throw IllegalArgumentException("7z entry mismatch for index ${metadata.index}")
        }

        val stream = archive.getInputStream(sevenZEntry)
        return object : InputStream() {
            override fun read(): Int = stream.read()

            override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)

            override fun close() {
                try {
                    stream.close()
                } finally {
                    archive.close()
                }
            }
        }
    }

    override fun close() {
        isClosed = true
        metadataByIndexAndPath = null
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

    private fun SevenZArchiveEntry.isImageFile(): Boolean =
        !isDirectory && ArchiveEntrySupport.isImageEntry(name ?: "")

    private fun SevenZArchiveEntry.requireName(): String =
        name ?: throw IOException("Encountered 7z entry without a name.")

    private fun SevenZFile.readEntryAtIndex(targetIndex: Int): SevenZArchiveEntry? {
        var index = 0
        var entry = nextEntry
        while (entry != null) {
            if (index == targetIndex) {
                return entry
            }
            index++
            entry = nextEntry
        }
        return null
    }
}
