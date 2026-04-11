package desktopApp.viewer

object ViewerStatePolicy {
    fun shouldStartArchiveLoad(
        requestedPath: String?,
        currentPath: String?,
        hasLoadedEntries: Boolean,
        loadingPath: String?
    ): Boolean {
        if (requestedPath.isNullOrBlank()) return false
        return requestedPath != currentPath || (!hasLoadedEntries && loadingPath != requestedPath)
    }

    fun initialIndexForLoad(resetPosition: Boolean, savedIndex: Int): Int {
        return if (resetPosition) 0 else savedIndex
    }

    fun shouldStopPlaybackForNewArchive(
        requestedPath: String?,
        currentPath: String?,
        isPlaying: Boolean
    ): Boolean {
        if (!isPlaying) return false
        if (requestedPath.isNullOrBlank()) return false
        return requestedPath != currentPath
    }

    fun clampIndex(index: Int, lastIndex: Int): Int = index.coerceIn(0, lastIndex)

    fun clampIntervalSeconds(seconds: Int): Int = seconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)

    const val MIN_INTERVAL_SECONDS: Int = 1
    const val MAX_INTERVAL_SECONDS: Int = 30
}
