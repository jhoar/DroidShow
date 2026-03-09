package showlio.app.ui.viewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import showlio.app.R
import showlio.app.archive.ArchiveEntryRef
import showlio.app.archive.ArchiveReader
import showlio.app.archive.ArchiveReaderFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class ViewerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val strictImageDecodeChecks: Boolean = false
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private var imageEntries: List<ArchiveEntryRef> = emptyList()
    private var slideshowJob: Job? = null
    private var loadingUri: Uri? = null
    private var displayOrder: MutableList<Int> = mutableListOf()
    private var activeReaderUri: Uri? = null
    private var activeReader: ArchiveReader? = null

    init {
        restoreSavedState()
    }

    fun loadArchiveIfNeeded(uri: Uri?) {
        val shouldLoad = ViewerStatePolicy.shouldStartArchiveLoad(
            requestedUri = uri?.toString(),
            currentUri = _uiState.value.archiveUri?.toString(),
            hasLoadedEntries = imageEntries.isNotEmpty(),
            loadingUri = loadingUri?.toString()
        )
        if (!shouldLoad || uri == null) return

        if (
            ViewerStatePolicy.shouldStopPlaybackForNewArchive(
                requestedUri = uri.toString(),
                currentUri = _uiState.value.archiveUri?.toString(),
                isPlaying = _uiState.value.isPlaying
            )
        ) {
            setPlaying(playing = false, restartLoop = false)
            stopSlideshowLoop()
        }

        loadArchive(uri, resetPosition = true)
    }

    fun togglePlayback() {
        if (imageEntries.isEmpty()) {
            setPlaying(playing = false, restartLoop = false)
            return
        }

        val shouldPlay = !_uiState.value.isPlaying
        setPlaying(playing = shouldPlay)
    }

    fun setSlideshowIntervalSeconds(seconds: Int) {
        val clampedSeconds = seconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)
        val intervalMs = clampedSeconds * 1_000L
        _uiState.value = _uiState.value.copy(slideshowIntervalMs = intervalMs)
        savedStateHandle[KEY_SLIDESHOW_INTERVAL_MS] = intervalMs
        restartSlideshowLoopIfNeeded()
    }

    fun setDisplayMode(displayMode: ViewerUiState.DisplayMode) {
        if (_uiState.value.displayMode == displayMode) return

        val currentEntry = imageEntries.getOrNull(_uiState.value.currentIndex)
        _uiState.value = _uiState.value.copy(displayMode = displayMode)
        savedStateHandle[KEY_DISPLAY_MODE] = displayMode.name
        rebuildDisplayOrder(currentEntry)
        restartSlideshowLoopIfNeeded()
    }

    private fun loadArchive(uri: Uri, resetPosition: Boolean) {
        loadingUri = uri
        updateLoadingState(uri)

        val initialIndex = ViewerStatePolicy.initialIndexForLoad(
            resetPosition = resetPosition,
            savedIndex = savedStateHandle[KEY_CURRENT_INDEX] ?: 0
        )
        savedStateHandle[KEY_CURRENT_INDEX] = initialIndex

        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val reader = ensureActiveReader(uri)
                    reader.listImageEntries()
                }
            }

            result.onSuccess { entries ->
                loadingUri = null
                imageEntries = entries
                if (entries.isEmpty()) {
                    clearContentWithError(
                        message = getApplication<Application>().getString(R.string.error_no_images)
                    )
                    stopSlideshowLoop()
                    return@onSuccess
                }

                val restoredIndex = ViewerStatePolicy.clampIndex(
                    index = savedStateHandle[KEY_CURRENT_INDEX] ?: 0,
                    lastIndex = entries.lastIndex
                )
                val restoredEntry = entries[restoredIndex]
                rebuildDisplayOrder(restoredEntry)
                showEntry(restoredIndex)
                restartSlideshowLoopIfNeeded()
            }.onFailure { throwable ->
                closeActiveReader()
                loadingUri = null
                clearContentWithError(message = errorMessageFor(throwable))
                stopSlideshowLoop()
            }
        }
    }

    private fun setPlaying(playing: Boolean, restartLoop: Boolean = true) {
        _uiState.value = _uiState.value.copy(isPlaying = playing)
        syncSavedStateFromUiState()
        if (restartLoop) {
            restartSlideshowLoopIfNeeded()
        }
    }

    private fun updateLoadingState(uri: Uri?) {
        _uiState.value = _uiState.value.copy(
            archiveUri = uri,
            isLoading = uri != null,
            errorMessage = null
        )
        syncSavedStateFromUiState()
    }

    private fun clearContentWithError(message: String, stopPlayback: Boolean = true) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isPlaying = if (stopPlayback) false else _uiState.value.isPlaying,
            currentIndex = 0,
            totalCount = 0,
            bitmap = null,
            errorMessage = message
        )
        syncSavedStateFromUiState()
    }

    private fun syncSavedStateFromUiState() {
        val state = _uiState.value
        savedStateHandle[KEY_IS_PLAYING] = state.isPlaying
        savedStateHandle[KEY_ARCHIVE_URI] = state.archiveUri?.toString()
        savedStateHandle[KEY_CURRENT_INDEX] = state.currentIndex
        savedStateHandle[KEY_SLIDESHOW_INTERVAL_MS] = state.slideshowIntervalMs
        savedStateHandle[KEY_DISPLAY_MODE] = state.displayMode.name
    }

    private suspend fun showEntry(index: Int) {
        if (imageEntries.isEmpty()) return

        val entry = imageEntries[index]
        val decodeResult = runCatching {
            withContext(Dispatchers.IO) {
                val reader = ensureActiveReader(entry.archiveUri)
                ViewerImageDecoder.decode(
                    streamProvider = { reader.openEntryStream(entry) },
                    strictChecks = strictImageDecodeChecks
                )
            }
        }

        decodeResult.onSuccess { bitmap ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentIndex = index,
                totalCount = imageEntries.size,
                bitmap = bitmap,
                errorMessage = null
            )
            savedStateHandle[KEY_CURRENT_INDEX] = index
        }.onFailure {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                bitmap = null,
                errorMessage = getApplication<Application>().getString(R.string.error_corrupt_image)
            )
        }
    }

    private suspend fun ensureActiveReader(uri: Uri): ArchiveReader {
        val currentReader = activeReader
        if (currentReader != null && activeReaderUri == uri) {
            return currentReader
        }

        closeActiveReader()
        val newReader = ArchiveReaderFactory.create(getApplication(), uri)
        activeReader = newReader
        activeReaderUri = uri
        return newReader
    }

    private fun closeActiveReader() {
        activeReader?.close()
        activeReader = null
        activeReaderUri = null
    }

    override fun onCleared() {
        stopSlideshowLoop()
        closeActiveReader()
        super.onCleared()
    }

    private fun restartSlideshowLoopIfNeeded() {
        stopSlideshowLoop()
        if (!_uiState.value.isPlaying || imageEntries.isEmpty()) {
            return
        }

        slideshowJob = viewModelScope.launch {
            while (_uiState.value.isPlaying && imageEntries.isNotEmpty()) {
                val intervalMs = _uiState.value.slideshowIntervalMs
                val nextIndex = nextIndexForMode(_uiState.value.currentIndex)
                showEntry(nextIndex)
                delay(intervalMs)
            }
        }
    }

    private fun nextIndexForMode(currentIndex: Int): Int {
        if (imageEntries.isEmpty()) return 0

        return when (_uiState.value.displayMode) {
            ViewerUiState.DisplayMode.SEQUENTIAL -> nextSequentialIndex(currentIndex)
            ViewerUiState.DisplayMode.RANDOM -> nextRandomIndex(currentIndex)
        }
    }

    private fun nextSequentialIndex(currentIndex: Int): Int =
        ViewerIndexSelector.nextSequentialIndex(currentIndex, imageEntries.size)

    private fun nextRandomIndex(currentIndex: Int): Int {
        ensureRandomDisplayOrder(currentIndex)
        val currentOrderPosition = currentRandomOrderPosition(currentIndex)
        val nextOrderPosition = nextRandomOrderPosition(currentOrderPosition, currentIndex)
        return resolveRandomImageIndex(nextOrderPosition, currentIndex)
    }

    private fun ensureRandomDisplayOrder(currentIndex: Int) {
        if (!isRandomDisplayOrderValid()) {
            rebuildDisplayOrder(imageEntries.getOrNull(currentIndex))
        }
    }

    private fun isRandomDisplayOrderValid(): Boolean {
        if (_uiState.value.displayMode != ViewerUiState.DisplayMode.RANDOM) return false
        return ViewerIndexSelector.isRandomDisplayOrderValid(displayOrder, imageEntries.size)
    }

    private fun currentRandomOrderPosition(currentIndex: Int): Int =
        ViewerIndexSelector.currentRandomOrderPosition(displayOrder, currentIndex)

    private fun nextRandomOrderPosition(currentOrderPosition: Int, currentIndex: Int): Int {
        if (ViewerIndexSelector.shouldRebuildRandomOrder(currentOrderPosition, displayOrder.lastIndex)) {
            rebuildDisplayOrder(imageEntries.getOrNull(currentIndex))
            return 1
        }

        return ViewerIndexSelector.nextRandomOrderPosition(currentOrderPosition)
    }

    private fun resolveRandomImageIndex(nextOrderPosition: Int, currentIndex: Int): Int =
        ViewerIndexSelector.resolveRandomImageIndex(displayOrder, nextOrderPosition, currentIndex)

    private fun rebuildDisplayOrder(currentEntry: ArchiveEntryRef?) {
        if (imageEntries.isEmpty()) {
            displayOrder = mutableListOf()
            return
        }

        val order = imageEntries.indices.toMutableList()
        if (_uiState.value.displayMode == ViewerUiState.DisplayMode.RANDOM) {
            order.shuffle(Random.Default)
            val currentIndex = currentEntry?.let { imageEntries.indexOf(it) } ?: -1
            if (currentIndex >= 0) {
                val foundPosition = order.indexOf(currentIndex)
                if (foundPosition > 0) {
                    order.removeAt(foundPosition)
                    order.add(0, currentIndex)
                }
            }
        }

        displayOrder = order
    }

    private fun stopSlideshowLoop() {
        slideshowJob?.cancel()
        slideshowJob = null
    }

    private fun restoreSavedState() {
        val uriString = savedStateHandle.get<String>(KEY_ARCHIVE_URI)
        val isPlaying = savedStateHandle.get<Boolean>(KEY_IS_PLAYING) ?: false
        val intervalMs = (savedStateHandle.get<Long>(KEY_SLIDESHOW_INTERVAL_MS) ?: DEFAULT_SLIDESHOW_INTERVAL_MS)
            .coerceIn(MIN_INTERVAL_SECONDS * 1_000L, MAX_INTERVAL_SECONDS * 1_000L)
        val displayMode = savedStateHandle.get<String>(KEY_DISPLAY_MODE)
            ?.let { runCatching { ViewerUiState.DisplayMode.valueOf(it) }.getOrNull() }
            ?: ViewerUiState.DisplayMode.SEQUENTIAL

        if (uriString.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                isPlaying = isPlaying,
                slideshowIntervalMs = intervalMs,
                displayMode = displayMode
            )
            return
        }

        val restoredUri = Uri.parse(uriString)
        _uiState.value = _uiState.value.copy(
            archiveUri = restoredUri,
            isLoading = true,
            isPlaying = isPlaying,
            slideshowIntervalMs = intervalMs,
            displayMode = displayMode
        )
        loadArchive(restoredUri, resetPosition = false)
    }

    companion object {
        const val MIN_INTERVAL_SECONDS = 1
        const val MAX_INTERVAL_SECONDS = 30
        private const val DEFAULT_SLIDESHOW_INTERVAL_MS = 3_000L
        private const val KEY_ARCHIVE_URI = "archive_uri"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val KEY_IS_PLAYING = "is_playing"
        private const val KEY_SLIDESHOW_INTERVAL_MS = "slideshow_interval_ms"
        private const val KEY_DISPLAY_MODE = "display_mode"
    }

    private fun errorMessageFor(throwable: Throwable): String {
        val app = getApplication<Application>()
        return when {
            throwable is IllegalArgumentException && throwable.message?.contains("Unsupported archive type") == true -> {
                app.getString(R.string.error_unsupported_archive)
            }

            throwable is IOException || throwable.cause is IOException -> {
                app.getString(R.string.error_corrupt_archive)
            }

            else -> app.getString(R.string.error_open_archive)
        }
    }
}
