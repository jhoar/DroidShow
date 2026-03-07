package showlio.app.archive

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import java.io.IOException
import java.io.InputStream

class RarArchiveReader(
    context: Context,
    private val archiveUri: Uri
) : ArchiveReader {

    private val tempArchive = TempArchiveFile(context, archiveUri, ".rar")

    private val imageEntries by lazy {
        Archive(tempArchive.file).use { archive ->
            archive.fileHeaders
                .mapIndexedNotNull { index, header ->
                    if (header.isDirectory) {
                        null
                    } else {
                        val name = header.fileName
                        if (!ArchiveEntrySupport.isImageEntry(name)) {
                            null
                        } else {
                            ArchiveEntryRef(
                                archiveUri = archiveUri,
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

    override fun listImageEntries(): List<ArchiveEntryRef> = imageEntries

    override fun openEntryStream(entry: ArchiveEntryRef): InputStream {
        require(entry.archiveUri == archiveUri) {
            "Archive entry does not belong to this archive reader."
        }

        val archive = Archive(tempArchive.file)
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
        tempArchive.cleanup()
    }
}
