package desktopApp.viewer

import desktopApp.policy.ViewerDisplayMode
import java.util.prefs.Preferences

data class ViewerPersistedState(
    val archivePath: String?,
    val currentIndex: Int,
    val isPlaying: Boolean,
    val autoplayOnLoad: Boolean,
    val slideshowIntervalMs: Long,
    val displayMode: ViewerDisplayMode
)

interface ViewerPersistence {
    fun load(): ViewerPersistedState?

    fun save(state: ViewerPersistedState)
}

class PreferencesViewerPersistence(
    private val preferences: Preferences = Preferences.userRoot().node("showlio/desktop/viewer"),
    private val platform: DesktopPlatform = DesktopPlatform.current()
) : ViewerPersistence {

    override fun load(): ViewerPersistedState? {
        if (!preferences.getBoolean(KEY_HAS_STATE, false)) return null

        val archivePath = preferences.get(KEY_ARCHIVE_PATH, null)
        val currentIndex = preferences.getInt(KEY_CURRENT_INDEX, 0)
        val isPlaying = preferences.getBoolean(KEY_IS_PLAYING, false)
        val autoplayOnLoad = preferences.getBoolean(KEY_AUTOPLAY_ON_LOAD, false)
        val slideshowIntervalMs = preferences.getLong(KEY_SLIDESHOW_INTERVAL_MS, DEFAULT_SLIDESHOW_INTERVAL_MS)
        val displayMode = preferences.get(KEY_DISPLAY_MODE, ViewerDisplayMode.SEQUENTIAL.name)
            ?.let { runCatching { ViewerDisplayMode.valueOf(it) }.getOrNull() }
            ?: ViewerDisplayMode.SEQUENTIAL

        return ViewerPersistedState(
            archivePath = platform.persistedArchivePath(archivePath),
            currentIndex = currentIndex,
            isPlaying = platform.persistedPlaybackState(isPlaying),
            autoplayOnLoad = autoplayOnLoad,
            slideshowIntervalMs = slideshowIntervalMs,
            displayMode = displayMode
        )
    }

    override fun save(state: ViewerPersistedState) {
        preferences.putBoolean(KEY_HAS_STATE, true)
        val persistedArchivePath = platform.persistedArchivePath(state.archivePath)
        if (persistedArchivePath.isNullOrBlank()) {
            preferences.remove(KEY_ARCHIVE_PATH)
        } else {
            preferences.put(KEY_ARCHIVE_PATH, persistedArchivePath)
        }
        preferences.putInt(KEY_CURRENT_INDEX, state.currentIndex)
        preferences.putBoolean(KEY_IS_PLAYING, platform.persistedPlaybackState(state.isPlaying))
        preferences.putBoolean(KEY_AUTOPLAY_ON_LOAD, state.autoplayOnLoad)
        preferences.putLong(KEY_SLIDESHOW_INTERVAL_MS, state.slideshowIntervalMs)
        preferences.put(KEY_DISPLAY_MODE, state.displayMode.name)
    }

    interface DesktopPlatform {
        fun persistedArchivePath(path: String?): String?

        fun persistedPlaybackState(isPlaying: Boolean): Boolean

        companion object {
            fun current(): DesktopPlatform {
                val osName = System.getProperty("os.name").orEmpty()
                return if (osName.startsWith("Windows", ignoreCase = true)) Windows else Default
            }
        }
    }

    private data object Default : DesktopPlatform {
        override fun persistedArchivePath(path: String?): String? = path

        override fun persistedPlaybackState(isPlaying: Boolean): Boolean = isPlaying
    }

    private data object Windows : DesktopPlatform {
        override fun persistedArchivePath(path: String?): String? = null

        override fun persistedPlaybackState(isPlaying: Boolean): Boolean = false
    }

    private companion object {
        const val KEY_HAS_STATE = "has_state"
        const val KEY_ARCHIVE_PATH = "archive_path"
        const val KEY_CURRENT_INDEX = "current_index"
        const val KEY_IS_PLAYING = "is_playing"
        const val KEY_AUTOPLAY_ON_LOAD = "autoplay_on_load"
        const val KEY_SLIDESHOW_INTERVAL_MS = "slideshow_interval_ms"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val DEFAULT_SLIDESHOW_INTERVAL_MS = 3_000L
    }
}
