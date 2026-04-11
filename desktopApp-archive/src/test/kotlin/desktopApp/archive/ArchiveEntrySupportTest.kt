package desktopApp.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveEntrySupportTest {

    @Test
    fun `isImageEntry accepts supported extensions case-insensitively`() {
        assertTrue(ArchiveEntrySupport.isImageEntry("chapter/page.JPG"))
        assertTrue(ArchiveEntrySupport.isImageEntry("chapter/page.jpeg"))
        assertTrue(ArchiveEntrySupport.isImageEntry("chapter/page.PnG"))
        assertTrue(ArchiveEntrySupport.isImageEntry("chapter/page.webp"))
        assertTrue(ArchiveEntrySupport.isImageEntry("chapter/page.GIF"))
        assertTrue(ArchiveEntrySupport.isImageEntry("chapter/page.bmp"))
        assertTrue(ArchiveEntrySupport.isImageEntry("chapter/page.avif"))
        assertTrue(ArchiveEntrySupport.isImageEntry("chapter/page.heif"))
        assertTrue(ArchiveEntrySupport.isImageEntry("chapter/page.HEIC"))
    }

    @Test
    fun `isImageEntry rejects unsupported or extension-less paths`() {
        assertFalse(ArchiveEntrySupport.isImageEntry("chapter/page.txt"))
        assertFalse(ArchiveEntrySupport.isImageEntry("chapter/page"))
        assertFalse(ArchiveEntrySupport.isImageEntry("chapter/.nomedia"))
    }

    @Test
    fun `naturalPathComparator sorts numerically and case-insensitively`() {
        val sorted = listOf(
            "page10.jpg",
            "page2.jpg",
            "Page3.jpg",
            "page01.jpg",
            "page1.jpg"
        ).sortedWith(ArchiveEntrySupport.naturalPathComparator)

        assertEquals(
            listOf("page1.jpg", "page01.jpg", "page2.jpg", "Page3.jpg", "page10.jpg"),
            sorted
        )
    }

    @Test
    fun `naturalPathComparator compares nested paths naturally`() {
        val sorted = listOf(
            "chapter10/page2.jpg",
            "chapter2/page10.jpg",
            "chapter2/page2.jpg",
            "chapter2/page1.jpg"
        ).sortedWith(ArchiveEntrySupport.naturalPathComparator)

        assertEquals(
            listOf(
                "chapter2/page1.jpg",
                "chapter2/page2.jpg",
                "chapter2/page10.jpg",
                "chapter10/page2.jpg"
            ),
            sorted
        )
    }
}
