package desktopApp.viewer

import desktopApp.policy.ViewerDisplayMode
import kotlinx.coroutines.flow.StateFlow

class DesktopViewerController(
    private val viewModel: ViewerViewModel = ViewerViewModel()
) {
    val uiState: StateFlow<ViewerUiState> get() = viewModel.uiState
    val state get() = viewModel.uiState.value

    fun loadArchive(path: String?) {
        viewModel.loadArchiveIfNeeded(path)
    }

    fun togglePlayback() {
        viewModel.togglePlayback()
    }

    fun setSlideshowIntervalSeconds(seconds: Int) {
        viewModel.setSlideshowIntervalSeconds(seconds)
    }

    fun setDisplayMode(displayMode: ViewerDisplayMode) {
        viewModel.setDisplayMode(displayMode)
    }

    fun setAutoplayOnLoad(enabled: Boolean) {
        viewModel.setAutoplayOnLoad(enabled)
    }

    fun advance() {
        viewModel.advanceOnce()
    }

    fun close() {
        viewModel.close()
    }
}
