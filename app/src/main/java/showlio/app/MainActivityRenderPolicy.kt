package showlio.app

internal data class PlaybackButtonState(
    val isEnabled: Boolean,
    val iconResId: Int,
    val contentDescriptionResId: Int
)

internal object MainActivityRenderPolicy {
    fun buildPositionText(
        currentIndex: Int,
        totalCount: Int,
        archiveFileName: String,
        errorMessage: String?,
        slideshowPositionFormatter: (current: Int, total: Int, archiveName: String) -> String
    ): String {
        return if (totalCount > 0) {
            slideshowPositionFormatter(currentIndex + 1, totalCount, archiveFileName)
        } else {
            errorMessage ?: archiveFileName
        }
    }

    fun playbackButtonState(
        totalCount: Int,
        hasBitmap: Boolean,
        isLoading: Boolean,
        isPlaying: Boolean
    ): PlaybackButtonState {
        val canControlSlideshow = totalCount > 0 && hasBitmap && !isLoading
        val iconResId: Int
        val contentDescriptionResId: Int
        if (isPlaying) {
            iconResId = android.R.drawable.ic_media_pause
            contentDescriptionResId = R.string.pause_slideshow
        } else {
            iconResId = android.R.drawable.ic_media_play
            contentDescriptionResId = R.string.start_slideshow
        }

        return PlaybackButtonState(
            isEnabled = canControlSlideshow,
            iconResId = iconResId,
            contentDescriptionResId = contentDescriptionResId
        )
    }
}
