package showlio.app.ui.viewer

import android.graphics.Bitmap
import android.net.Uri

data class ViewerUiState(
    val archiveUri: Uri? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val slideshowIntervalMs: Long = 3_000L,
    val displayMode: DisplayMode = DisplayMode.SEQUENTIAL,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val bitmap: Bitmap? = null,
    val errorMessage: String? = null
) {
    enum class DisplayMode {
        SEQUENTIAL,
        RANDOM
    }
}
