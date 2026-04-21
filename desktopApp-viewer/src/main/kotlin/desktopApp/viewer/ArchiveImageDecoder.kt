package desktopApp.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.IOException
import java.io.InputStream
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

interface ArchiveImageDecoder {
    fun decode(stream: InputStream, maxDimension: Int): ImageBitmap
}

class DefaultArchiveImageDecoder : ArchiveImageDecoder {
    override fun decode(stream: InputStream, maxDimension: Int): ImageBitmap {
        val bytes = stream.readBytes()
        val image = try {
            SkiaImage.makeFromEncoded(bytes)
        } catch (e: Exception) {
            throw IOException("Unsupported image format", e)
        }
        val (targetW, targetH) = computeTargetSize(image.width, image.height, maxDimension)
        val source = if (targetW == image.width && targetH == image.height) {
            image
        } else {
            val surface = Surface.makeRasterN32Premul(targetW, targetH)
            surface.canvas.drawImageRect(
                image,
                Rect.makeXYWH(0f, 0f, targetW.toFloat(), targetH.toFloat()),
                SamplingMode.LINEAR
            )
            surface.makeImageSnapshot()
        }
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(source.width, source.height)
        source.readPixels(bitmap)
        return bitmap.asImageBitmap()
    }

    private fun computeTargetSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        if (maxDimension <= 0 || maxOf(width, height) <= maxDimension) return Pair(width, height)
        val scale = maxDimension.toDouble() / maxOf(width, height)
        return Pair((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
    }
}
