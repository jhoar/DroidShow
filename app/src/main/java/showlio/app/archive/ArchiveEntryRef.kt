package showlio.app.archive

import android.net.Uri

data class ArchiveEntryRef(
    val archiveUri: Uri,
    val entryPath: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val index: Int
)
