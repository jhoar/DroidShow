package showlio.app.ui.viewer

import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewerImageDecoderTest {

    @Test
    fun `decode rejects oversized image dimensions from metadata in strict mode`() {
        val oversizedPngBytes = oversizedPngBytes()
        val error = runCatching {
            ViewerImageDecoder.decode(strictChecks = true) {
                ByteArrayInputStream(oversizedPngBytes)
            }
        }.exceptionOrNull()

        assertTrue(error is ImageDecodeException)
    }

    @Test
    fun `decode succeeds for normal image`() {
        val imageBytes = normalPngBytes(width = 200, height = 120)

        val decoded = ViewerImageDecoder.decode { ByteArrayInputStream(imageBytes) }

        assertEquals(200, decoded.width)
        assertEquals(120, decoded.height)
    }

    @Test
    fun `decode in non strict mode opens stream only once`() {
        val imageBytes = normalPngBytes(width = 200, height = 120)
        val openCount = AtomicInteger(0)

        val decoded = ViewerImageDecoder.decode(strictChecks = false) {
            openCount.incrementAndGet()
            ByteArrayInputStream(imageBytes)
        }

        assertEquals(200, decoded.width)
        assertEquals(120, decoded.height)
        assertEquals(1, openCount.get())
    }

    private fun oversizedPngBytes(): ByteArray {
        val base = normalPngBytes(width = 1, height = 1)
        val out = base.copyOf()
        val ihdrStart = PNG_SIGNATURE_BYTES + CHUNK_HEADER_BYTES

        ByteBuffer.wrap(out, ihdrStart, 13).apply {
            putInt(12_001)
            putInt(120)
        }

        val typeOffset = PNG_SIGNATURE_BYTES + CHUNK_LENGTH_BYTES
        val crcInputLength = CHUNK_TYPE_BYTES + 13
        val crc = CRC32().apply {
            update(out, typeOffset, crcInputLength)
        }.value.toInt()

        ByteBuffer.wrap(out, ihdrStart + 13, CHUNK_CRC_BYTES).putInt(crc)
        return out
    }

    private fun normalPngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    companion object {
        private const val PNG_SIGNATURE_BYTES = 8
        private const val CHUNK_LENGTH_BYTES = 4
        private const val CHUNK_TYPE_BYTES = 4
        private const val CHUNK_CRC_BYTES = 4
        private const val CHUNK_HEADER_BYTES = CHUNK_LENGTH_BYTES + CHUNK_TYPE_BYTES
    }
}
