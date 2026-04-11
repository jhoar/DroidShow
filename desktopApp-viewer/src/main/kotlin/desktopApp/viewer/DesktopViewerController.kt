package desktopApp.viewer

import desktopApp.policy.ViewerDisplayMode
import desktopApp.policy.ViewerIndexSelector

class DesktopViewerController {
    var state: DesktopViewerState = DesktopViewerState()
        private set

    fun advance() {
        val totalCount = state.totalCount
        if (totalCount <= 0) return

        val nextIndex = when (state.displayMode) {
            ViewerDisplayMode.SEQUENTIAL -> {
                ViewerIndexSelector.nextSequentialIndex(state.currentIndex, totalCount)
            }
            ViewerDisplayMode.RANDOM -> {
                val nextPosition = ViewerIndexSelector.nextRandomOrderPosition(state.currentOrderPosition)
                ViewerIndexSelector.resolveRandomImageIndex(state.randomOrder, nextPosition, state.currentIndex)
            }
        }

        state = state.copy(currentIndex = nextIndex)
    }
}
