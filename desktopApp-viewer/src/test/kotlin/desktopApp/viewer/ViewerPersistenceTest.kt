package desktopApp.viewer

import desktopApp.policy.ViewerDisplayMode
import java.util.UUID
import java.util.prefs.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerPersistenceTest {

    @Test
    fun windowsDoesNotPersistArchivePathOrPlaybackState() {
        val preferences = tempPreferences()
        val persistence = PreferencesViewerPersistence(
            preferences = preferences,
            platform = windowsPlatform()
        )

        persistence.save(
            ViewerPersistedState(
                archivePath = "C:/images/sample.cbz",
                currentIndex = 3,
                isPlaying = true,
                slideshowIntervalMs = 5_000L,
                displayMode = ViewerDisplayMode.RANDOM
            )
        )

        assertNull(preferences.get("archive_path", null))
        assertFalse(preferences.getBoolean("is_playing", true))

        val restored = persistence.load()
        assertNull(restored?.archivePath)
        assertFalse(restored?.isPlaying ?: true)
        assertEquals(3, restored?.currentIndex)
        assertEquals(5_000L, restored?.slideshowIntervalMs)
        assertEquals(ViewerDisplayMode.RANDOM, restored?.displayMode)

        preferences.removeNode()
    }

    @Test
    fun defaultPlatformPersistsArchivePathAndPlaybackState() {
        val preferences = tempPreferences()
        val persistence = PreferencesViewerPersistence(
            preferences = preferences,
            platform = defaultPlatform()
        )

        persistence.save(
            ViewerPersistedState(
                archivePath = "/tmp/sample.cbz",
                currentIndex = 1,
                isPlaying = true,
                slideshowIntervalMs = 4_000L,
                displayMode = ViewerDisplayMode.SEQUENTIAL
            )
        )

        val restored = persistence.load()
        assertEquals("/tmp/sample.cbz", restored?.archivePath)
        assertEquals(true, restored?.isPlaying)

        preferences.removeNode()
    }

    private fun tempPreferences(): Preferences {
        return Preferences.userRoot().node("showlio/tests/${UUID.randomUUID()}")
    }

    private fun windowsPlatform(): PreferencesViewerPersistence.DesktopPlatform {
        return object : PreferencesViewerPersistence.DesktopPlatform {
            override fun persistedArchivePath(path: String?): String? = null

            override fun persistedPlaybackState(isPlaying: Boolean): Boolean = false
        }
    }

    private fun defaultPlatform(): PreferencesViewerPersistence.DesktopPlatform {
        return object : PreferencesViewerPersistence.DesktopPlatform {
            override fun persistedArchivePath(path: String?): String? = path

            override fun persistedPlaybackState(isPlaying: Boolean): Boolean = isPlaying
        }
    }
}
