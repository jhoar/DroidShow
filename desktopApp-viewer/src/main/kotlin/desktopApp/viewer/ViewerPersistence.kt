package desktopApp.viewer

import desktopApp.policy.ViewerDisplayMode
import java.util.prefs.Preferences

data class ViewerPersistedState(
    val archivePath: String?,
    val currentIndex: Int,
    val isPlaying: Boolean,
    val slideshowIntervalMs: Long,
    val displayMode: ViewerDisplayMode
)

interface ViewerPersistence {
    fun load(): ViewerPersistedState?

    fun save(state: ViewerPersistedState)
}

class PreferencesViewerPersistence(
    private val preferences: Preferences = Preferences.userRoot().node("droidshow/desktop/viewer")
) : ViewerPersistence {

    override fun load(): ViewerPersistedState? {
        if (!preferences.getBoolean(KEY_HAS_STATE, false)) return null

        val archivePath = preferences.get(KEY_ARCHIVE_PATH, null)
        val currentIndex = preferences.getInt(KEY_CURRENT_INDEX, 0)
        val isPlaying = preferences.getBoolean(KEY_IS_PLAYING, false)
        val slideshowIntervalMs = preferences.getLong(KEY_SLIDESHOW_INTERVAL_MS, DEFAULT_SLIDESHOW_INTERVAL_MS)
        val displayMode = preferences.get(KEY_DISPLAY_MODE, ViewerDisplayMode.SEQUENTIAL.name)
            ?.let { runCatching { ViewerDisplayMode.valueOf(it) }.getOrNull() }
            ?: ViewerDisplayMode.SEQUENTIAL

        return ViewerPersistedState(
            archivePath = archivePath,
            currentIndex = currentIndex,
            isPlaying = isPlaying,
            slideshowIntervalMs = slideshowIntervalMs,
            displayMode = displayMode
        )
    }

    override fun save(state: ViewerPersistedState) {
        preferences.putBoolean(KEY_HAS_STATE, true)
        if (state.archivePath.isNullOrBlank()) {
            preferences.remove(KEY_ARCHIVE_PATH)
        } else {
            preferences.put(KEY_ARCHIVE_PATH, state.archivePath)
        }
        preferences.putInt(KEY_CURRENT_INDEX, state.currentIndex)
        preferences.putBoolean(KEY_IS_PLAYING, state.isPlaying)
        preferences.putLong(KEY_SLIDESHOW_INTERVAL_MS, state.slideshowIntervalMs)
        preferences.put(KEY_DISPLAY_MODE, state.displayMode.name)
    }

    private companion object {
        const val KEY_HAS_STATE = "has_state"
        const val KEY_ARCHIVE_PATH = "archive_path"
        const val KEY_CURRENT_INDEX = "current_index"
        const val KEY_IS_PLAYING = "is_playing"
        const val KEY_SLIDESHOW_INTERVAL_MS = "slideshow_interval_ms"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val DEFAULT_SLIDESHOW_INTERVAL_MS = 3_000L
    }
}
