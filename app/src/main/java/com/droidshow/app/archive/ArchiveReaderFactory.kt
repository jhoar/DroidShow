package com.droidshow.app.archive

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object ArchiveReaderFactory {
    fun create(context: Context, archiveUri: Uri): ArchiveReader {
        val archiveType = resolveArchiveType(context, archiveUri)
            ?: throw IllegalArgumentException("Unsupported archive type")

        return when (archiveType) {
            ArchiveType.ZIP -> ZipArchiveReader(context, archiveUri)
            ArchiveType.RAR -> RarArchiveReader(context, archiveUri)
            ArchiveType.SEVEN_Z -> SevenZArchiveReader(context, archiveUri)
        }
    }

    private fun resolveArchiveType(context: Context, archiveUri: Uri): ArchiveType? {
        val fileName = resolveDisplayName(context, archiveUri) ?: archiveUri.lastPathSegment
        val mimeType = context.contentResolver.getType(archiveUri)
        return ArchiveTypeResolver.resolve(fileName = fileName, mimeType = mimeType)
    }

    private fun resolveDisplayName(context: Context, archiveUri: Uri): String? {
        val resolver = context.contentResolver
        return resolver.query(archiveUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex == -1) return@use null
                cursor.getString(nameIndex)
            }
    }
}
