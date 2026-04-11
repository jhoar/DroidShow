package desktopApp.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerStatePolicyTest {

    @Test
    fun `shouldStartArchiveLoad returns false for null or blank requested uri`() {
        assertFalse(
            ViewerStatePolicy.shouldStartArchiveLoad(
                requestedUri = null,
                currentUri = "content://archive.cbz",
                hasLoadedEntries = true,
                loadingUri = null
            )
        )

        assertFalse(
            ViewerStatePolicy.shouldStartArchiveLoad(
                requestedUri = "",
                currentUri = null,
                hasLoadedEntries = false,
                loadingUri = null
            )
        )
    }

    @Test
    fun `shouldStartArchiveLoad prevents duplicate load when same uri already loaded`() {
        assertFalse(
            ViewerStatePolicy.shouldStartArchiveLoad(
                requestedUri = "content://archive.cbz",
                currentUri = "content://archive.cbz",
                hasLoadedEntries = true,
                loadingUri = null
            )
        )
    }

    @Test
    fun `shouldStartArchiveLoad prevents duplicate load while same uri is in-flight`() {
        assertFalse(
            ViewerStatePolicy.shouldStartArchiveLoad(
                requestedUri = "content://archive.cbz",
                currentUri = "content://archive.cbz",
                hasLoadedEntries = false,
                loadingUri = "content://archive.cbz"
            )
        )
    }

    @Test
    fun `shouldStartArchiveLoad allows first load and uri switches`() {
        assertTrue(
            ViewerStatePolicy.shouldStartArchiveLoad(
                requestedUri = "content://archive.cbz",
                currentUri = null,
                hasLoadedEntries = false,
                loadingUri = null
            )
        )

        assertTrue(
            ViewerStatePolicy.shouldStartArchiveLoad(
                requestedUri = "content://new.cbz",
                currentUri = "content://old.cbz",
                hasLoadedEntries = true,
                loadingUri = null
            )
        )
    }

    @Test
    fun `shouldStopPlaybackForNewArchive only stops when playback is active and uri changes`() {
        assertFalse(
            ViewerStatePolicy.shouldStopPlaybackForNewArchive(
                requestedUri = "content://new.cbz",
                currentUri = "content://old.cbz",
                isPlaying = false
            )
        )

        assertFalse(
            ViewerStatePolicy.shouldStopPlaybackForNewArchive(
                requestedUri = "content://same.cbz",
                currentUri = "content://same.cbz",
                isPlaying = true
            )
        )

        assertTrue(
            ViewerStatePolicy.shouldStopPlaybackForNewArchive(
                requestedUri = "content://new.cbz",
                currentUri = "content://old.cbz",
                isPlaying = true
            )
        )
    }

    @Test
    fun `initialIndexForLoad keeps current index across restore and resets for new archive`() {
        assertEquals(0, ViewerStatePolicy.initialIndexForLoad(resetPosition = true, savedIndex = 7))
        assertEquals(7, ViewerStatePolicy.initialIndexForLoad(resetPosition = false, savedIndex = 7))
    }

    @Test
    fun `clampIndex keeps index within entry bounds`() {
        assertEquals(0, ViewerStatePolicy.clampIndex(index = -3, lastIndex = 5))
        assertEquals(3, ViewerStatePolicy.clampIndex(index = 3, lastIndex = 5))
        assertEquals(5, ViewerStatePolicy.clampIndex(index = 9, lastIndex = 5))
    }
}
