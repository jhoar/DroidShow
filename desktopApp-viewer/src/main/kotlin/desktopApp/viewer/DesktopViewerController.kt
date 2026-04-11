package desktopApp.viewer

import desktopApp.policy.ViewerDisplayMode

class DesktopViewerController(
    private val viewModel: ViewerViewModel = ViewerViewModel()
) {
    val state get() = viewModel.uiState.value

    fun advance() {
        viewModel.advanceOnce()
    }

    fun setDisplayMode(displayMode: ViewerDisplayMode) {
        viewModel.setDisplayMode(displayMode)
    }

    fun close() {
        viewModel.close()
    }
}
