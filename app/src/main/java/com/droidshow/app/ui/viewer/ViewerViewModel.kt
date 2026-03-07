package com.droidshow.app.ui.viewer

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.droidshow.app.R
import com.droidshow.app.archive.ArchiveEntryRef
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

class ViewerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private var imageEntries: List<ArchiveEntryRef> = emptyList()
    private var slideshowJob: Job? = null
    private var loadingUri: Uri? = null

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
                    ArchiveReaderFactory.create(getApplication(), uri).use { reader ->
                        reader.listImageEntries()
                    }
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
                showEntry(restoredIndex)
                restartSlideshowLoopIfNeeded()
            }.onFailure { throwable ->
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

    private fun showEntry(index: Int) {
        if (imageEntries.isEmpty()) return

        val entry = imageEntries[index]
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ArchiveReaderFactory.create(getApplication(), entry.archiveUri).use { reader ->
                    reader.openEntryStream(entry).use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
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
    }

    private fun restartSlideshowLoopIfNeeded() {
        stopSlideshowLoop()
        if (!_uiState.value.isPlaying || imageEntries.isEmpty()) {
            return
        }

        slideshowJob = viewModelScope.launch {
            while (_uiState.value.isPlaying && imageEntries.isNotEmpty()) {
                delay(_uiState.value.slideshowIntervalMs)
                val nextIndex = (_uiState.value.currentIndex + 1) % imageEntries.size
                showEntry(nextIndex)
            }
        }
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

        if (uriString.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                isPlaying = isPlaying,
                slideshowIntervalMs = intervalMs
            )
            return
        }

        val restoredUri = Uri.parse(uriString)
        _uiState.value = _uiState.value.copy(
            archiveUri = restoredUri,
            isLoading = true,
            isPlaying = isPlaying,
            slideshowIntervalMs = intervalMs
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
