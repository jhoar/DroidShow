package showlio.app.archive

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal class ArchiveTempCache(private val context: Context) {

    private val cacheDir: File = File(context.cacheDir, CACHE_DIRECTORY_NAME).apply {
        mkdirs()
    }

    init {
        cleanupStaleFiles()
    }

    fun getOrCreate(archiveUri: Uri, suffix: String): File {
        cleanupStaleFiles()

        val metadata = resolveMetadata(archiveUri)
        val key = buildCacheKey(archiveUri, metadata)
        val cacheFile = File(cacheDir, "$key$suffix")

        synchronized(lock) {
            if (cacheFile.exists() && cacheFile.isFile) {
                cacheFile.setLastModified(System.currentTimeMillis())
                ArchiveTempCacheMetrics.recordHit()
                return cacheFile
            }

            ArchiveTempCacheMetrics.recordMiss()
            writeToCache(cacheFile, archiveUri)
            evictIfNeeded(protectedFile = cacheFile)
            return cacheFile
        }
    }

    private fun resolveMetadata(uri: Uri): SourceMetadata {
        if (uri.scheme == "file") {
            val source = uri.path?.let(::File)
            if (source != null && source.exists()) {
                return SourceMetadata(size = source.length(), modifiedAt = source.lastModified())
            }
        }

        var size: Long? = null
        var modifiedAt: Long? = null

        try {
            val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            if (descriptor != null) {
                descriptor.use {
                    if (it.length >= 0) {
                        size = it.length
                    }
                }
            }
        } catch (_: Throwable) {
            // Some providers/test environments can throw reflective-access exceptions here.
        }

        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }

                    val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
                        modifiedAt = cursor.getLong(modifiedIndex)
                    }
                }
            }
        } catch (_: Throwable) {
            // Best-effort metadata lookup; keying falls back to URI when unavailable.
        }

        return SourceMetadata(size = size, modifiedAt = modifiedAt)
    }

    private fun buildCacheKey(uri: Uri, metadata: SourceMetadata): String {
        val payload = buildString {
            append(uri.toString())
            append('|')
            append(metadata.size ?: -1L)
            append('|')
            append(metadata.modifiedAt ?: -1L)
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray())

        return digest.joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }

    private fun writeToCache(targetFile: File, archiveUri: Uri) {
        val tempFile = File(cacheDir, "${targetFile.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        try {
            requireNotNull(context.contentResolver.openInputStream(archiveUri)) {
                "Unable to open archive URI: $archiveUri"
            }.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            moveIntoPlace(tempFile = tempFile, targetFile = targetFile)
            targetFile.setLastModified(System.currentTimeMillis())
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }

    private fun moveIntoPlace(tempFile: File, targetFile: File) {
        if (targetFile.exists() && !targetFile.delete()) {
            throw IllegalStateException("Unable to replace existing cache file: ${targetFile.absolutePath}")
        }

        if (!tempFile.renameTo(targetFile)) {
            throw IllegalStateException("Unable to finalize cache file: ${targetFile.absolutePath}")
        }
    }

    private fun cleanupStaleFiles() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            cacheDir.listFiles().orEmpty().forEach { file ->
                if (!file.isFile) {
                    return@forEach
                }
                if (file.name.endsWith(TEMP_FILE_SUFFIX)) {
                    file.delete()
                    return@forEach
                }
                val age = now - file.lastModified()
                if (age > CACHE_TTL_MS && file.delete()) {
                    ArchiveTempCacheMetrics.recordEviction()
                }
            }
        }
    }

    private fun evictIfNeeded(protectedFile: File) {
        val files = cacheDir.listFiles().orEmpty()
            .filter { it.isFile && !it.name.endsWith(TEMP_FILE_SUFFIX) }
            .sortedBy { it.lastModified() }
            .toMutableList()

        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_CACHE_BYTES) {
            return
        }

        for (file in files) {
            if (totalBytes <= MAX_CACHE_BYTES) {
                break
            }
            if (file.absolutePath == protectedFile.absolutePath) {
                continue
            }

            val fileSize = file.length()
            if (file.delete()) {
                totalBytes -= fileSize
                ArchiveTempCacheMetrics.recordEviction()
            }
        }

        if (totalBytes > MAX_CACHE_BYTES && protectedFile.length() > MAX_CACHE_BYTES) {
            Log.d(
                LOG_TAG,
                "archive temp cache oversize entry retained (size=${protectedFile.length()}, budget=$MAX_CACHE_BYTES)"
            )
        }
    }

    private data class SourceMetadata(
        val size: Long?,
        val modifiedAt: Long?
    )

    internal object ArchiveTempCacheMetrics {
        private val cacheHits = AtomicLong(0)
        private val cacheMisses = AtomicLong(0)
        private val evictions = AtomicLong(0)

        fun recordHit() {
            val current = cacheHits.incrementAndGet()
            Log.d(LOG_TAG, "archive temp cache hit (hits=$current)")
        }

        fun recordMiss() {
            val current = cacheMisses.incrementAndGet()
            Log.d(LOG_TAG, "archive temp cache miss (misses=$current)")
        }

        fun recordEviction() {
            val current = evictions.incrementAndGet()
            Log.d(LOG_TAG, "archive temp cache eviction (evictions=$current)")
        }

        fun snapshot(): CacheMetricsSnapshot = CacheMetricsSnapshot(
            hits = cacheHits.get(),
            misses = cacheMisses.get(),
            evictions = evictions.get()
        )
    }

    internal data class CacheMetricsSnapshot(
        val hits: Long,
        val misses: Long,
        val evictions: Long
    )

    private companion object {
        private const val CACHE_DIRECTORY_NAME = "archive-reader"
        private const val TEMP_FILE_SUFFIX = ".tmp"
        private const val LOG_TAG = "ArchiveTempCache"
        private val lock = Any()

        const val MAX_CACHE_BYTES: Long = 512L * 1024L * 1024L
        const val CACHE_TTL_MS: Long = 24L * 60L * 60L * 1000L
    }
}
