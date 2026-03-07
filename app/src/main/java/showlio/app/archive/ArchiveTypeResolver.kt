package showlio.app.archive

import java.util.Locale

internal enum class ArchiveType {
    ZIP,
    RAR,
    SEVEN_Z
}

internal object ArchiveTypeResolver {
    private val zipExtensions = setOf("zip", "cbz")
    private val rarExtensions = setOf("rar", "cbr")
    private val sevenZExtensions = setOf("7z", "cb7")

    private val zipMimeTypes = setOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/vnd.comicbook+zip",
        "application/x-cbz"
    )

    private val rarMimeTypes = setOf(
        "application/x-rar-compressed",
        "application/vnd.rar",
        "application/vnd.comicbook-rar",
        "application/x-cbr"
    )

    private val sevenZMimeTypes = setOf(
        "application/x-7z-compressed",
        "application/x-cb7"
    )

    fun resolve(fileName: String?, mimeType: String?): ArchiveType? {
        val extension = fileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.US)
            .orEmpty()
        val normalizedMime = mimeType?.lowercase(Locale.US)

        return when {
            extension in zipExtensions || normalizedMime in zipMimeTypes -> ArchiveType.ZIP
            extension in rarExtensions || normalizedMime in rarMimeTypes -> ArchiveType.RAR
            extension in sevenZExtensions || normalizedMime in sevenZMimeTypes -> ArchiveType.SEVEN_Z
            else -> null
        }
    }
}
