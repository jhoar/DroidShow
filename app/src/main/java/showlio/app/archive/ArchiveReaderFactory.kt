package showlio.app.archive

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.net.URLDecoder

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
        val mimeType = context.contentResolver.getType(archiveUri)
        val nameCandidates = resolveNameCandidates(context, archiveUri)

        for (name in nameCandidates) {
            ArchiveTypeResolver.resolve(fileName = name, mimeType = mimeType)?.let { return it }
        }

        return ArchiveTypeResolver.resolve(fileName = null, mimeType = mimeType)
    }

    private fun resolveNameCandidates(context: Context, archiveUri: Uri): List<String> {
        val candidates = linkedSetOf<String>()

        resolveDisplayName(context, archiveUri)?.let(candidates::add)
        archiveUri.lastPathSegment?.let(candidates::add)

        val decodedPath = runCatching {
            URLDecoder.decode(archiveUri.toString(), Charsets.UTF_8.name())
        }.getOrNull()
        decodedPath?.substringAfterLast('/')?.let(candidates::add)

        val documentName = runCatching {
            DocumentsContract.getDocumentId(archiveUri)
                .substringAfterLast(':', missingDelimiterValue = "")
        }.getOrNull()
        if (!documentName.isNullOrBlank()) {
            candidates.add(documentName)
        }

        return candidates.filter { it.isNotBlank() }
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
