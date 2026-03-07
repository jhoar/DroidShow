package com.droidshow.app.ui.viewer

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.droidshow.app.R
import com.droidshow.app.archive.ArchiveEntryRef
import com.droidshow.app.archive.ArchiveReader
import com.droidshow.app.archive.ArchiveReaderFactory
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
    private val savedStateHandle: SavedStateHandle
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
            _uiState.value = _uiState.value.copy(isPlaying = false)
            savedStateHandle[KEY_IS_PLAYING] = false
            stopSlideshowLoop()
        }

        loadArchive(uri, resetPosition = true)
    }

    fun togglePlayback() {
        if (imageEntries.isEmpty()) {
            _uiState.value = _uiState.value.copy(isPlaying = false)
            savedStateHandle[KEY_IS_PLAYING] = false
            return
        }

        val shouldPlay = !_uiState.value.isPlaying
        _uiState.value = _uiState.value.copy(isPlaying = shouldPlay)
        savedStateHandle[KEY_IS_PLAYING] = shouldPlay
        restartSlideshowLoopIfNeeded()
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
        _uiState.value = _uiState.value.copy(
            archiveUri = uri,
            isLoading = true,
            errorMessage = null
        )
        savedStateHandle[KEY_ARCHIVE_URI] = uri.toString()

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
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isPlaying = false,
                        currentIndex = 0,
                        totalCount = 0,
                        bitmap = null,
                        errorMessage = getApplication<Application>().getString(R.string.error_no_images)
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
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isPlaying = false,
                    bitmap = null,
                    errorMessage = errorMessageFor(throwable)
                )
                stopSlideshowLoop()
            }
        }
    }

    private suspend fun showEntry(index: Int) {
        if (imageEntries.isEmpty()) return

        val entry = imageEntries[index]
        val bitmap = withContext(Dispatchers.IO) {
            val reader = ensureActiveReader(entry.archiveUri)
            reader.openEntryStream(entry).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            currentIndex = index,
            totalCount = imageEntries.size,
            bitmap = bitmap,
            errorMessage = null
        )
        savedStateHandle[KEY_CURRENT_INDEX] = index
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
                val iterationStartedAt = System.currentTimeMillis()
                val nextIndex = nextIndexForMode(_uiState.value.currentIndex)
                showEntry(nextIndex)
                val elapsedMs = System.currentTimeMillis() - iterationStartedAt
                val remainingDelayMs = (intervalMs - elapsedMs).coerceAtLeast(0L)
                if (remainingDelayMs > 0L) {
                    delay(remainingDelayMs)
                }
            }
        }
    }

    private fun nextIndexForMode(currentIndex: Int): Int {
        if (imageEntries.isEmpty()) return 0

        return when (_uiState.value.displayMode) {
            ViewerUiState.DisplayMode.SEQUENTIAL -> (currentIndex + 1) % imageEntries.size
            ViewerUiState.DisplayMode.RANDOM -> {
                val currentOrderIndex = displayOrder.indexOf(currentIndex)
                val nextOrderIndex = if (currentOrderIndex < 0 || currentOrderIndex >= displayOrder.lastIndex) {
                    rebuildDisplayOrder(imageEntries.getOrNull(currentIndex))
                    1
                } else {
                    currentOrderIndex + 1
                }

                displayOrder.getOrElse(nextOrderIndex.coerceAtMost(displayOrder.lastIndex)) { currentIndex }
            }
        }
    }

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
