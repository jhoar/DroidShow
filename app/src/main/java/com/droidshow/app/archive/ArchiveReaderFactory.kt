package com.droidshow.app.archive

import android.content.Context
import android.net.Uri
import java.util.Locale

object ArchiveReaderFactory {
    fun create(context: Context, archiveUri: Uri): ArchiveReader {
        val extension = archiveUri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.US)
            .orEmpty()

        return when (extension) {
            "zip", "cbz" -> ZipArchiveReader(context, archiveUri)
            "rar", "cbr" -> RarArchiveReader(context, archiveUri)
            "7z", "cb7" -> SevenZArchiveReader(context, archiveUri)
            else -> throw IllegalArgumentException("Unsupported archive type: .$extension")
        }
    }
}
