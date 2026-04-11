package desktopApp.archive

import com.github.junrar.Archive
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path

class RarArchiveReader(
    private val archivePath: Path
) : DesktopArchiveReader {

    private val imageEntries by lazy {
        Archive(archivePath.toFile()).use { archive ->
            archive.fileHeaders
                .mapIndexedNotNull { index, header ->
                    if (header.isDirectory) {
                        null
                    } else {
                        val name = header.fileName
                        if (!ArchiveEntrySupport.isImageEntry(name)) {
                            null
                        } else {
                            DesktopArchiveEntryRef(
                                archivePath = archivePath,
                                entryPath = name,
                                compressedSize = header.fullPackSize,
                                uncompressedSize = header.fullUnpackSize,
                                index = index
                            )
                        }
                    }
                }
                .sortedWith(compareBy(ArchiveEntrySupport.naturalPathComparator) { it.entryPath })
        }
    }

    override fun listImageEntries(): List<DesktopArchiveEntryRef> = imageEntries

    override fun openEntryStream(entry: DesktopArchiveEntryRef): InputStream {
        require(entry.archivePath == archivePath) {
            "Archive entry does not belong to this archive reader."
        }

        val archive = Archive(archivePath.toFile())
        val header = archive.fileHeaders.getOrNull(entry.index)
            ?: throw IllegalArgumentException("RAR entry index out of range: ${entry.index}")

        if (header.fileName != entry.entryPath) {
            archive.close()
            throw IllegalArgumentException("RAR entry mismatch for index ${entry.index}")
        }

        val stream = archive.getInputStream(header)
            ?: throw IOException("Unable to open RAR entry stream: ${entry.entryPath}")

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
    }
}
