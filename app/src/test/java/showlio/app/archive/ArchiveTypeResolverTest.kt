package showlio.app.archive

import desktopApp.archive.ArchiveKind
import desktopApp.archive.ArchiveTypeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveTypeResolverTest {

    @Test
    fun `resolve detects archive by extension`() {
        assertEquals(ArchiveKind.ZIP, ArchiveTypeResolver.resolve("chapter.cbz", null))
        assertEquals(ArchiveKind.RAR, ArchiveTypeResolver.resolve("chapter.cbr", null))
        assertEquals(ArchiveKind.SEVEN_Z, ArchiveTypeResolver.resolve("chapter.7z", null))
    }

    @Test
    fun `resolve detects archive by mime type when extension is unavailable`() {
        assertEquals(ArchiveKind.ZIP, ArchiveTypeResolver.resolve(null, "application/zip"))
        assertEquals(ArchiveKind.RAR, ArchiveTypeResolver.resolve(null, "application/vnd.rar"))
        assertEquals(ArchiveKind.RAR, ArchiveTypeResolver.resolve(null, "application/x-rar"))
        assertEquals(ArchiveKind.RAR, ArchiveTypeResolver.resolve(null, "application/rar"))
        assertEquals(ArchiveKind.SEVEN_Z, ArchiveTypeResolver.resolve(null, "application/x-7z-compressed"))
    }

    @Test
    fun `resolve returns null for unsupported archive metadata`() {
        assertNull(ArchiveTypeResolver.resolve("chapter.pdf", "application/pdf"))
    }
}
