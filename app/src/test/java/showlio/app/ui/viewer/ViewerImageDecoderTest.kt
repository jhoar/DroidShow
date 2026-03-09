package showlio.app.ui.viewer

import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerImageDecoderTest {

    @Test
    fun `decode rejects oversized image dimensions from metadata`() {
        val oversizedPngBytes = pngBytes(width = 20_000, height = 20_000)

        val error = runCatching {
            ViewerImageDecoder.decode { ByteArrayInputStream(oversizedPngBytes) }
        }.exceptionOrNull()

        assertTrue(error is ImageDecodeException)
        assertTrue(error?.message?.contains("safe limits") == true)
    }

    @Test
    fun `decode succeeds for normal image`() {
        val imageBytes = normalPngBytes(width = 200, height = 120)

        val decoded = ViewerImageDecoder.decode { ByteArrayInputStream(imageBytes) }

        assertEquals(200, decoded.width)
        assertEquals(120, decoded.height)
    }

    private fun normalPngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        val ihdrData = ByteBuffer.allocate(13)
            .putInt(width)
            .putInt(height)
            .put(8)
            .put(6)
            .put(0)
            .put(0)
            .put(0)
            .array()

        val idatData = byteArrayOf(
            0x78.toByte(), 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01
        )

        return ByteArrayOutputStream().use { output ->
            output.write(signature)
            output.write(chunk("IHDR", ihdrData))
            output.write(chunk("IDAT", idatData))
            output.write(chunk("IEND", byteArrayOf()))
            output.toByteArray()
        }
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value.toInt()

        return ByteBuffer.allocate(4 + typeBytes.size + data.size + 4)
            .putInt(data.size)
            .put(typeBytes)
            .put(data)
            .putInt(crc)
            .array()
    }
}
