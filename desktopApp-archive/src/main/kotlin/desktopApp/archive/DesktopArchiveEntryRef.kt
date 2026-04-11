package desktopApp.archive

import java.nio.file.Path

data class DesktopArchiveEntryRef(
    val archivePath: Path,
    val entryPath: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val index: Int
)
