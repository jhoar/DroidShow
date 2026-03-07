package showlio.app.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveTypeResolverTest {

    @Test
    fun `resolve detects archive by extension`() {
        assertEquals(ArchiveType.ZIP, ArchiveTypeResolver.resolve("chapter.cbz", null))
        assertEquals(ArchiveType.RAR, ArchiveTypeResolver.resolve("chapter.cbr", null))
        assertEquals(ArchiveType.SEVEN_Z, ArchiveTypeResolver.resolve("chapter.7z", null))
    }

    @Test
    fun `resolve detects archive by mime type when extension is unavailable`() {
        assertEquals(ArchiveType.ZIP, ArchiveTypeResolver.resolve(null, "application/zip"))
        assertEquals(ArchiveType.RAR, ArchiveTypeResolver.resolve(null, "application/vnd.rar"))
        assertEquals(ArchiveType.SEVEN_Z, ArchiveTypeResolver.resolve(null, "application/x-7z-compressed"))
    }

    @Test
    fun `resolve returns null for unsupported archive metadata`() {
        assertNull(ArchiveTypeResolver.resolve("chapter.pdf", "application/pdf"))
    }
}
