package desktopApp.viewer

import desktopApp.policy.ViewerDisplayMode

data class DesktopViewerState(
    val displayMode: ViewerDisplayMode = ViewerDisplayMode.SEQUENTIAL,
    val currentIndex: Int = 0,
    val totalCount: Int = 1,
    val randomOrder: IntArray = intArrayOf(0),
    val currentOrderPosition: Int = 0
)
