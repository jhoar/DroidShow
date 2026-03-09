package showlio.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

internal object ViewerImageDecoder {
    private const val BYTES_PER_PIXEL = 4L
    private const val MAX_WIDTH = 10_000
    private const val MAX_HEIGHT = 10_000
    private const val MAX_TOTAL_PIXELS = 40_000_000L
    private const val MAX_BITMAP_BYTES = 32L * 1024L * 1024L

    fun decode(streamProvider: () -> InputStream): Bitmap {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        try {
            streamProvider().use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
        } catch (error: RuntimeException) {
            throw ImageDecodeException("Unable to read image dimensions.", error)
        }

        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) {
            throw ImageDecodeException("Unable to read image dimensions.")
        }

        val totalPixels = width.toLong() * height.toLong()
        if (width > MAX_WIDTH || height > MAX_HEIGHT || totalPixels > MAX_TOTAL_PIXELS) {
            throw ImageDecodeException("Image dimensions exceed safe limits.")
        }

        val sampleSize = calculateInSampleSize(width, height)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inDither = false
        }

        return try {
            streamProvider().use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw ImageDecodeException("Failed to decode image.")
        } catch (error: RuntimeException) {
            throw ImageDecodeException("Failed to decode image.", error)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (estimatedBitmapBytes(width, height, sampleSize) > MAX_BITMAP_BYTES) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun estimatedBitmapBytes(width: Int, height: Int, sampleSize: Int): Long {
        val sampledWidth = (width / sampleSize).coerceAtLeast(1)
        val sampledHeight = (height / sampleSize).coerceAtLeast(1)
        return sampledWidth.toLong() * sampledHeight.toLong() * BYTES_PER_PIXEL
    }
}

internal class ImageDecodeException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
