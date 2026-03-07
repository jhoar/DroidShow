package showlio.app.slideshow

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import showlio.app.archive.ArchiveEntryRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.random.Random

private val Context.slideshowDataStore by preferencesDataStore(name = "slideshow_settings")

class SlideshowController(
    private val context: Context,
    lifecycleOwner: LifecycleOwner,
    private val entries: List<ArchiveEntryRef>,
    initialMode: SlideshowMode = SlideshowMode.SEQUENTIAL,
    initialIntervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
    random: Random = Random.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val lifecycleScope = lifecycleOwner.lifecycle.coroutineScope
    private val navigator = SlideshowNavigator(totalCount = entries.size, random = random)

    private val _mode = MutableStateFlow(initialMode)
    val mode: StateFlow<SlideshowMode> = _mode.asStateFlow()

    private val _intervalSeconds = MutableStateFlow(initialIntervalSeconds.coerceAtLeast(MIN_INTERVAL_SECONDS))
    val intervalSeconds: StateFlow<Int> = _intervalSeconds.asStateFlow()

    private val _currentEntry = MutableStateFlow<ArchiveEntryRef?>(null)
    val currentEntry: StateFlow<ArchiveEntryRef?> = _currentEntry.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        lifecycleScope.launch(ioDispatcher) {
            loadPersistedSettings(initialMode, initialIntervalSeconds)
        }

        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(_isPlaying, _intervalSeconds) { playing, seconds -> playing to seconds }
                    .collectLatest { (playing, seconds) ->
                        if (!playing || !navigator.hasEntries()) {
                            return@collectLatest
                        }

                        while (_isPlaying.value) {
                            delay(seconds * 1_000L)
                            if (_isPlaying.value) {
                                next()
                            }
                        }
                    }
            }
        }
    }

    fun start() {
        if (!navigator.hasEntries()) return

        if (_currentEntry.value == null) {
            val startIndex = navigator.startIndex(_mode.value)
            publishIndex(startIndex)
        }

        _isPlaying.value = true
    }

    fun pause() {
        _isPlaying.value = false
    }

    fun resume() {
        if (!navigator.hasEntries()) return

        if (_currentEntry.value == null) {
            start()
            return
        }

        _isPlaying.value = true
    }

    fun next() {
        if (!navigator.hasEntries()) return

        val nextIndex = navigator.nextIndex(_mode.value)
        publishIndex(nextIndex)
    }

    fun previous() {
        if (!navigator.hasEntries()) return

        val previousIndex = navigator.previousIndex() ?: return
        publishIndex(previousIndex)
    }

    fun setMode(mode: SlideshowMode) {
        if (_mode.value == mode) return

        _mode.value = mode
        navigator.onModeChanged(mode)
        persistSettings()
    }

    fun setInterval(seconds: Int) {
        val updated = seconds.coerceAtLeast(MIN_INTERVAL_SECONDS)
        if (_intervalSeconds.value == updated) return

        _intervalSeconds.value = updated
        persistSettings()
    }

    private fun publishIndex(index: Int) {
        _currentEntry.value = entries[index]
    }

    private fun persistSettings() {
        val modeToSave = _mode.value
        val intervalToSave = _intervalSeconds.value
        lifecycleScope.launch(ioDispatcher) {
            context.slideshowDataStore.edit { prefs ->
                prefs[KEY_MODE] = modeToSave.name
                prefs[KEY_INTERVAL_SECONDS] = intervalToSave
            }
        }
    }

    private suspend fun loadPersistedSettings(defaultMode: SlideshowMode, defaultInterval: Int) {
        val persisted = context.slideshowDataStore.data.map { prefs ->
            val modeValue = prefs[KEY_MODE]
            val persistedMode = modeValue?.let { runCatching { SlideshowMode.valueOf(it) }.getOrNull() } ?: defaultMode
            val persistedInterval = prefs[KEY_INTERVAL_SECONDS] ?: defaultInterval
            persistedMode to persistedInterval.coerceAtLeast(MIN_INTERVAL_SECONDS)
        }

        persisted.collectLatest { (persistedMode, persistedInterval) ->
            _mode.value = persistedMode
            _intervalSeconds.value = persistedInterval
            navigator.onModeChanged(persistedMode)
        }
    }

    enum class SlideshowMode {
        SEQUENTIAL,
        RANDOM
    }

    companion object {
        private const val DEFAULT_INTERVAL_SECONDS = 5
        private const val MIN_INTERVAL_SECONDS = 1

        private val KEY_MODE = stringPreferencesKey("mode")
        private val KEY_INTERVAL_SECONDS = intPreferencesKey("interval_seconds")
    }
}
