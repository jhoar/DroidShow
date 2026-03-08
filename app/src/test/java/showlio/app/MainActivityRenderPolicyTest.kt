package showlio.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityRenderPolicyTest {
    @Test
    fun `buildPositionText returns slideshow position when archive has images`() {
        val text = MainActivityRenderPolicy.buildPositionText(
            currentIndex = 2,
            totalCount = 10,
            archiveFileName = "sample.cbz",
            errorMessage = "error"
        ) { current, total, archiveName ->
            "$current/$total • $archiveName"
        }

        assertEquals("3/10 • sample.cbz", text)
    }

    @Test
    fun `buildPositionText returns error when archive has no images and error exists`() {
        val text = MainActivityRenderPolicy.buildPositionText(
            currentIndex = 0,
            totalCount = 0,
            archiveFileName = "sample.cbz",
            errorMessage = "No images were found in this archive."
        ) { current, total, archiveName ->
            "$current/$total • $archiveName"
        }

        assertEquals("No images were found in this archive.", text)
    }

    @Test
    fun `buildPositionText returns archive file name when archive has no images and no error`() {
        val text = MainActivityRenderPolicy.buildPositionText(
            currentIndex = 0,
            totalCount = 0,
            archiveFileName = "sample.cbz",
            errorMessage = null
        ) { current, total, archiveName ->
            "$current/$total • $archiveName"
        }

        assertEquals("sample.cbz", text)
    }

    @Test
    fun `playbackButtonState returns pause icon and enabled when playing and controllable`() {
        val state = MainActivityRenderPolicy.playbackButtonState(
            totalCount = 5,
            hasBitmap = true,
            isLoading = false,
            isPlaying = true
        )

        assertTrue(state.isEnabled)
        assertEquals(android.R.drawable.ic_media_pause, state.iconResId)
        assertEquals(R.string.pause_slideshow, state.contentDescriptionResId)
    }

    @Test
    fun `playbackButtonState returns play icon and disabled when loading`() {
        val state = MainActivityRenderPolicy.playbackButtonState(
            totalCount = 5,
            hasBitmap = true,
            isLoading = true,
            isPlaying = false
        )

        assertFalse(state.isEnabled)
        assertEquals(android.R.drawable.ic_media_play, state.iconResId)
        assertEquals(R.string.start_slideshow, state.contentDescriptionResId)
    }
}
