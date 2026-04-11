package desktopApp.archive

import java.nio.file.Files
import java.nio.file.Path

object DesktopArchiveReaderFactory {
    fun create(archivePath: String): DesktopArchiveReader = create(Path.of(archivePath))

    fun create(archivePath: Path): DesktopArchiveReader {
        val archiveType = resolveArchiveType(archivePath)
            ?: throw IllegalArgumentException("Unsupported archive type")

        return when (archiveType) {
            ArchiveKind.ZIP -> ZipArchiveReader(archivePath)
            ArchiveKind.RAR -> RarArchiveReader(archivePath)
            ArchiveKind.SEVEN_Z -> SevenZArchiveReader(archivePath)
        }
    }

    private fun resolveArchiveType(archivePath: Path): ArchiveKind? {
        val fileName = archivePath.fileName?.toString()
        val mimeType = runCatching { Files.probeContentType(archivePath) }.getOrNull()

        return ArchiveTypeResolver.resolve(fileName = fileName, mimeType = mimeType)
            ?: ArchiveTypeResolver.resolve(fileName = fileName, mimeType = null)
    }
}
