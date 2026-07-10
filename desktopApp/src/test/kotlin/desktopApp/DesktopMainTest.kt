package desktopApp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DesktopMainTest {

    @Test
    fun `tryResolveFileUrl resolves triple-slash file URL with Japanese characters`() {
        val result = tryResolveFileUrl("file:///tmp/日本語.zip")
        assertNotNull(result)
        assertEquals("日本語.zip", result?.fileName?.toString())
    }

    @Test
    fun `tryResolveFileUrl resolves triple-slash file URL with ASCII path`() {
        val result = tryResolveFileUrl("file:///tmp/archive.cbz")
        assertNotNull(result)
        assertEquals("archive.cbz", result?.fileName?.toString())
    }

    @Test
    fun `tryResolveFileUrl resolves single-slash file URL with Japanese characters`() {
        val result = tryResolveFileUrl("file:/tmp/日本語.zip")
        assertNotNull(result)
        assertEquals("日本語.zip", result?.fileName?.toString())
    }

    @Test
    fun `tryResolveFileUrl resolves file URL with non-Japanese non-ASCII characters`() {
        val result = tryResolveFileUrl("file:///tmp/archiv-über.zip")
        assertNotNull(result)
        assertEquals("archiv-über.zip", result?.fileName?.toString())
    }

    @Test
    fun `tryResolveFileUrl resolves nested path with Japanese directory and filename`() {
        val result = tryResolveFileUrl("file:///tmp/漫画/日本語.zip")
        assertNotNull(result)
        assertEquals("日本語.zip", result?.fileName?.toString())
    }
}
