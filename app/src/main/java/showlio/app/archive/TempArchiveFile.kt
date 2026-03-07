package showlio.app.archive

import android.content.Context
import android.net.Uri
import java.io.File

internal class TempArchiveFile(
    context: Context,
    private val archiveUri: Uri,
    suffix: String
) {
    val file: File = File.createTempFile("archive-reader-", suffix, context.cacheDir).apply {
        deleteOnExit()
    }

    init {
        requireNotNull(context.contentResolver.openInputStream(archiveUri)) {
            "Unable to open archive URI: $archiveUri"
        }.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun cleanup() {
        file.delete()
    }
}
