package showlio.app.ui.viewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerViewModelStateSyncTest {

    @Test
    fun `setPlaying updates ui state and saved state without restarting loop when disabled`() {
        val (viewModel, savedStateHandle) = createViewModel()

        viewModel.invokeSetPlaying(playing = true, restartLoop = false)

        assertTrue(viewModel.uiState.value.isPlaying)
        assertTrue(savedStateHandle.get<Boolean>("is_playing") == true)
        assertEquals(viewModel.uiState.value.currentIndex, savedStateHandle.get<Int>("current_index"))
        assertEquals(
            viewModel.uiState.value.slideshowIntervalMs,
            savedStateHandle.get<Long>("slideshow_interval_ms")
        )
        assertEquals(
            viewModel.uiState.value.displayMode.name,
            savedStateHandle.get<String>("display_mode")
        )
    }

    @Test
    fun `updateLoadingState syncs archive uri and clears loading error`() {
        val (viewModel, savedStateHandle) = createViewModel()
        val uri = Uri.parse("content://archive.cbz")

        viewModel.invokeUpdateLoadingState(uri)

        assertEquals(uri, viewModel.uiState.value.archiveUri)
        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(uri.toString(), savedStateHandle.get<String>("archive_uri"))
        assertEquals(viewModel.uiState.value.currentIndex, savedStateHandle.get<Int>("current_index"))
        assertEquals(
            viewModel.uiState.value.slideshowIntervalMs,
            savedStateHandle.get<Long>("slideshow_interval_ms")
        )
        assertEquals(
            viewModel.uiState.value.displayMode.name,
            savedStateHandle.get<String>("display_mode")
        )
    }

    @Test
    fun `clearContentWithError resets content and optionally keeps playback`() {
        val (viewModel, savedStateHandle) = createViewModel()
        viewModel.invokeSetPlaying(playing = true, restartLoop = false)

        viewModel.invokeClearContentWithError(message = "boom", stopPlayback = false)

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isPlaying)
        assertEquals(0, viewModel.uiState.value.currentIndex)
        assertEquals(0, viewModel.uiState.value.totalCount)
        assertNull(viewModel.uiState.value.bitmap)
        assertEquals("boom", viewModel.uiState.value.errorMessage)

        assertTrue(savedStateHandle.get<Boolean>("is_playing") == true)
        assertEquals(0, savedStateHandle.get<Int>("current_index"))
        assertEquals(viewModel.uiState.value.archiveUri?.toString(), savedStateHandle.get<String>("archive_uri"))
        assertEquals(
            viewModel.uiState.value.slideshowIntervalMs,
            savedStateHandle.get<Long>("slideshow_interval_ms")
        )
        assertEquals(
            viewModel.uiState.value.displayMode.name,
            savedStateHandle.get<String>("display_mode")
        )
    }

    private fun createViewModel(): Pair<ViewerViewModel, SavedStateHandle> {
        val savedStateHandle = SavedStateHandle()
        val viewModel = ViewerViewModel(
            application = Application(),
            savedStateHandle = savedStateHandle
        )
        return viewModel to savedStateHandle
    }

    private fun ViewerViewModel.invokeSetPlaying(playing: Boolean, restartLoop: Boolean) {
        val method = ViewerViewModel::class.java.getDeclaredMethod(
            "setPlaying",
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(this, playing, restartLoop)
    }

    private fun ViewerViewModel.invokeUpdateLoadingState(uri: Uri?) {
        val method = ViewerViewModel::class.java.getDeclaredMethod("updateLoadingState", Uri::class.java)
        method.isAccessible = true
        method.invoke(this, uri)
    }

    private fun ViewerViewModel.invokeClearContentWithError(message: String, stopPlayback: Boolean) {
        val method = ViewerViewModel::class.java.getDeclaredMethod(
            "clearContentWithError",
            String::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(this, message, stopPlayback)
    }
}
