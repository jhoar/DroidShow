package desktopApp.policy

object ViewerStatePolicy {
    fun shouldStartArchiveLoad(
        requestedUri: String?,
        currentUri: String?,
        hasLoadedEntries: Boolean,
        loadingUri: String?
    ): Boolean {
        if (requestedUri.isNullOrBlank()) return false
        return requestedUri != currentUri || (!hasLoadedEntries && loadingUri != requestedUri)
    }

    fun initialIndexForLoad(resetPosition: Boolean, savedIndex: Int): Int {
        return if (resetPosition) 0 else savedIndex
    }

    fun shouldStopPlaybackForNewArchive(
        requestedUri: String?,
        currentUri: String?,
        isPlaying: Boolean
    ): Boolean {
        if (!isPlaying) return false
        if (requestedUri.isNullOrBlank()) return false
        return requestedUri != currentUri
    }

    fun clampIndex(index: Int, lastIndex: Int): Int {
        return index.coerceIn(0, lastIndex)
    }
}
