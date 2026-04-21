package desktopApp.viewer

import androidx.compose.ui.graphics.ImageBitmap
import desktopApp.archive.DesktopArchiveEntryRef
import desktopApp.policy.ViewerDisplayMode

data class ViewerUiState(
    val archivePath: String? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val autoplayOnLoad: Boolean = false,
    val slideshowIntervalMs: Long = 3_000L,
    val displayMode: ViewerDisplayMode = ViewerDisplayMode.SEQUENTIAL,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentEntry: DesktopArchiveEntryRef? = null,
    val bitmap: ImageBitmap? = null,
    val errorMessage: String? = null
)
