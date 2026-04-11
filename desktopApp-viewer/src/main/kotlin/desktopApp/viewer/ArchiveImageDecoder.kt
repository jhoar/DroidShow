package desktopApp.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.IOException
import java.io.InputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.stream.ImageInputStream

interface ArchiveImageDecoder {
    fun decode(stream: InputStream, maxDimension: Int): ImageBitmap
}

class DefaultArchiveImageDecoder : ArchiveImageDecoder {
    override fun decode(stream: InputStream, maxDimension: Int): ImageBitmap {
        val imageStream = ImageIO.createImageInputStream(stream)
            ?: throw IOException("Unable to create image input stream")
        imageStream.use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                ?: throw IOException("Unsupported image format")
            reader.use {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                val sample = computeSubsampling(width, height, maxDimension)
                val params = ImageReadParam().apply {
                    if (sample > 1) {
                        setSourceSubsampling(sample, sample, 0, 0)
                    }
                }
                val image = reader.read(0, params)
                    ?: throw IOException("Failed to decode image")
                return image.toComposeImageBitmap()
            }
        }
    }

    private fun computeSubsampling(width: Int, height: Int, maxDimension: Int): Int {
        if (maxDimension <= 0) return 1
        val longest = maxOf(width, height)
        if (longest <= maxDimension) return 1
        return (longest.toDouble() / maxDimension.toDouble()).toInt().coerceAtLeast(1)
    }
}

private inline fun <T : AutoCloseable?, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        this?.close()
    }
}
