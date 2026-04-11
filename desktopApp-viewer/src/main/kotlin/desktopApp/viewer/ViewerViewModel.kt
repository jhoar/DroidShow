package desktopApp.viewer

import desktopApp.archive.DesktopArchiveEntryRef
import desktopApp.archive.DesktopArchiveReader
import desktopApp.archive.DesktopArchiveReaderFactory
import desktopApp.policy.ViewerDisplayMode
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class ViewerViewModel(
    private val persistence: ViewerPersistence = PreferencesViewerPersistence(),
    private val readerFactory: (String) -> DesktopArchiveReader = DesktopArchiveReaderFactory::create,
    private val imageDecoder: ArchiveImageDecoder = DefaultArchiveImageDecoder(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val randomInt: (Int) -> Int = Random.Default::nextInt,
    private val maxBitmapDimension: Int = DEFAULT_MAX_BITMAP_DIMENSION
) {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private var imageEntries: List<DesktopArchiveEntryRef> = emptyList()
    private var slideshowJob: Job? = null
    private var loadingPath: String? = null
    private var randomOrder: IntArray = IntArray(0)
    private var positionByImageIndex: IntArray = IntArray(0)
    private var currentOrderPosition: Int = -1
    private var activeReaderPath: String? = null
    private var activeReader: DesktopArchiveReader? = null
    private var imageLoadJob: Job? = null
    private var imageLoadRequestId: Long = 0L

    init {
        restorePersistedState()
    }

    fun loadArchiveIfNeeded(path: String?) {
        val shouldLoad = ViewerStatePolicy.shouldStartArchiveLoad(
            requestedPath = path,
            currentPath = _uiState.value.archivePath,
            hasLoadedEntries = imageEntries.isNotEmpty(),
            loadingPath = loadingPath
        )
        if (!shouldLoad || path == null) return

        if (ViewerStatePolicy.shouldStopPlaybackForNewArchive(path, _uiState.value.archivePath, _uiState.value.isPlaying)) {
            setPlaying(playing = false, restartLoop = false)
            stopSlideshowLoop()
        }

        loadArchive(path, resetPosition = true)
    }

    fun togglePlayback() {
        if (imageEntries.isEmpty()) {
            setPlaying(playing = false, restartLoop = false)
            return
        }

        setPlaying(playing = !_uiState.value.isPlaying)
    }

    fun setSlideshowIntervalSeconds(seconds: Int) {
        val intervalSeconds = ViewerStatePolicy.clampIntervalSeconds(seconds)
        _uiState.value = _uiState.value.copy(slideshowIntervalMs = intervalSeconds * 1_000L)
        syncPersistenceFromState()
        restartSlideshowLoopIfNeeded()
    }

    fun setDisplayMode(displayMode: ViewerDisplayMode) {
        if (_uiState.value.displayMode == displayMode) return

        val currentIndex = _uiState.value.currentIndex
        _uiState.value = _uiState.value.copy(displayMode = displayMode)
        rebuildDisplayOrder(currentIndex)
        syncPersistenceFromState()
        restartSlideshowLoopIfNeeded()
    }

    fun advanceOnce() {
        if (imageEntries.isEmpty()) return
        val nextIndex = nextIndexForMode(_uiState.value.currentIndex)
        showEntry(nextIndex)
    }

    fun close() {
        stopSlideshowLoop()
        cancelImageLoad()
        closeActiveReader()
    }

    private fun loadArchive(path: String, resetPosition: Boolean) {
        loadingPath = path
        updateLoadingState(path)
        val initialIndex = ViewerStatePolicy.initialIndexForLoad(resetPosition, _uiState.value.currentIndex)
        _uiState.value = _uiState.value.copy(currentIndex = initialIndex)
        syncPersistenceFromState()

        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val reader = ensureActiveReader(path)
                    reader.listImageEntries()
                }
            }

            result.onSuccess { entries ->
                loadingPath = null
                imageEntries = entries
                if (entries.isEmpty()) {
                    clearContentWithError("No images found in archive")
                    stopSlideshowLoop()
                    return@onSuccess
                }

                val restoredIndex = ViewerStatePolicy.clampIndex(_uiState.value.currentIndex, entries.lastIndex)
                rebuildDisplayOrder(restoredIndex)
                showEntry(restoredIndex)
                restartSlideshowLoopIfNeeded()
            }.onFailure { throwable ->
                closeActiveReader()
                loadingPath = null
                clearContentWithError(errorMessageFor(throwable))
                stopSlideshowLoop()
            }
        }
    }

    private fun setPlaying(playing: Boolean, restartLoop: Boolean = true) {
        _uiState.value = _uiState.value.copy(isPlaying = playing)
        syncPersistenceFromState()
        if (restartLoop) restartSlideshowLoopIfNeeded()
    }

    private fun updateLoadingState(path: String?) {
        _uiState.value = _uiState.value.copy(archivePath = path, isLoading = path != null, errorMessage = null)
        syncPersistenceFromState()
    }

    private fun clearContentWithError(message: String, stopPlayback: Boolean = true) {
        imageEntries = emptyList()
        cancelImageLoad()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isPlaying = if (stopPlayback) false else _uiState.value.isPlaying,
            currentIndex = 0,
            totalCount = 0,
            currentEntry = null,
            bitmap = null,
            errorMessage = message
        )
        syncPersistenceFromState()
    }

    private fun syncPersistenceFromState() {
        val state = _uiState.value
        persistence.save(
            ViewerPersistedState(
                archivePath = state.archivePath,
                currentIndex = state.currentIndex,
                isPlaying = state.isPlaying,
                slideshowIntervalMs = state.slideshowIntervalMs,
                displayMode = state.displayMode
            )
        )
    }

    private fun showEntry(index: Int) {
        if (imageEntries.isEmpty()) return

        val entry = imageEntries[index]
        val requestId = nextImageLoadRequestId()
        cancelImageLoad()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            currentIndex = index,
            totalCount = imageEntries.size,
            currentEntry = entry,
            errorMessage = null
        )
        syncPersistenceFromState()

        imageLoadJob = scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val reader = ensureActiveReader(entry.archivePath.toString())
                    reader.openEntryStream(entry).use { stream ->
                        imageDecoder.decode(stream, maxDimension = maxBitmapDimension)
                    }
                }
            }

            result.onSuccess { bitmap ->
                if (!isImageLoadCurrent(requestId)) return@onSuccess
                _uiState.value = _uiState.value.copy(bitmap = bitmap, errorMessage = null)
            }.onFailure { throwable ->
                if (throwable is CancellationException || !isImageLoadCurrent(requestId)) return@onFailure
                _uiState.value = _uiState.value.copy(
                    bitmap = null,
                    errorMessage = errorMessageFor(throwable)
                )
            }
        }
    }

    private suspend fun ensureActiveReader(path: String): DesktopArchiveReader {
        val currentReader = activeReader
        if (currentReader != null && activeReaderPath == path) return currentReader

        closeActiveReader()
        val newReader = readerFactory(path)
        activeReader = newReader
        activeReaderPath = path
        return newReader
    }

    private fun closeActiveReader() {
        activeReader?.close()
        activeReader = null
        activeReaderPath = null
    }

    private fun restartSlideshowLoopIfNeeded() {
        stopSlideshowLoop()
        if (!_uiState.value.isPlaying || imageEntries.isEmpty()) return

        slideshowJob = scope.launch {
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
            ViewerDisplayMode.SEQUENTIAL -> ViewerIndexSelector.nextSequentialIndex(currentIndex, imageEntries.size)
            ViewerDisplayMode.RANDOM -> nextRandomIndex(currentIndex)
        }
    }

    private fun nextRandomIndex(currentIndex: Int): Int {
        ensureRandomDisplayOrder(currentIndex)
        val nextOrderPosition = nextRandomOrderPosition(currentOrderPosition, currentIndex)
        currentOrderPosition = nextOrderPosition
        return ViewerIndexSelector.resolveRandomImageIndex(randomOrder, nextOrderPosition, currentIndex)
    }

    private fun ensureRandomDisplayOrder(currentIndex: Int) {
        if (!hasExpectedRandomTraversalShape()) rebuildDisplayOrder(currentIndex)
    }

    private fun hasExpectedRandomTraversalShape(): Boolean {
        if (_uiState.value.displayMode != ViewerDisplayMode.RANDOM) return false
        val totalCount = imageEntries.size
        if (totalCount == 0) return false
        if (randomOrder.size != totalCount || positionByImageIndex.size != totalCount) return false
        return currentOrderPosition in 0 until totalCount
    }

    private fun nextRandomOrderPosition(currentOrderPosition: Int, currentIndex: Int): Int {
        if (ViewerIndexSelector.shouldRebuildRandomOrder(currentOrderPosition, randomOrder.lastIndex)) {
            rebuildDisplayOrder(currentIndex)
            return ViewerIndexSelector.nextRandomOrderPosition(this.currentOrderPosition)
        }
        return ViewerIndexSelector.nextRandomOrderPosition(currentOrderPosition)
    }

    private fun rebuildDisplayOrder(currentIndex: Int) {
        if (imageEntries.isEmpty()) {
            randomOrder = IntArray(0)
            positionByImageIndex = IntArray(0)
            currentOrderPosition = -1
            return
        }

        if (_uiState.value.displayMode == ViewerDisplayMode.RANDOM) {
            val traversalState = ViewerIndexSelector.rebuildRandomTraversalState(
                totalCount = imageEntries.size,
                currentIndex = currentIndex,
                randomInt = randomInt
            )
            randomOrder = traversalState.order
            positionByImageIndex = traversalState.positionByImageIndex
            currentOrderPosition = traversalState.currentOrderPosition
            return
        }

        randomOrder = IntArray(imageEntries.size) { it }
        positionByImageIndex = IntArray(imageEntries.size) { it }
        currentOrderPosition = currentIndex.coerceIn(0, imageEntries.lastIndex)
    }

    private fun stopSlideshowLoop() {
        slideshowJob?.cancel()
        slideshowJob = null
    }

    private fun cancelImageLoad() {
        imageLoadJob?.cancel()
        imageLoadJob = null
    }

    private fun nextImageLoadRequestId(): Long {
        imageLoadRequestId += 1L
        return imageLoadRequestId
    }

    private fun isImageLoadCurrent(requestId: Long): Boolean = imageLoadRequestId == requestId

    private fun restorePersistedState() {
        val restored = persistence.load() ?: return
        val clampedIntervalMs = ViewerStatePolicy
            .clampIntervalSeconds((restored.slideshowIntervalMs / 1_000L).toInt())
            .toLong() * 1_000L

        _uiState.value = _uiState.value.copy(
            archivePath = restored.archivePath,
            currentIndex = restored.currentIndex,
            isPlaying = restored.isPlaying,
            slideshowIntervalMs = clampedIntervalMs,
            displayMode = restored.displayMode,
            isLoading = !restored.archivePath.isNullOrBlank()
        )

        if (!restored.archivePath.isNullOrBlank()) {
            loadArchive(restored.archivePath, resetPosition = false)
        }
    }

    private fun errorMessageFor(throwable: Throwable): String {
        return when {
            throwable is IllegalArgumentException && throwable.message?.contains("Unsupported archive type") == true -> {
                "Unsupported archive format."
            }

            hasCause(throwable) { it is SecurityException || it is java.nio.file.AccessDeniedException } -> {
                "Permission denied or unable to read archive."
            }

            hasCause(throwable) { it is IOException } -> {
                "Archive appears to be corrupt."
            }

            else -> "Unable to open archive."
        }
    }

    private fun hasCause(throwable: Throwable, predicate: (Throwable) -> Boolean): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (predicate(current)) return true
            current = current.cause
        }
        return false
    }

    companion object {
        private const val DEFAULT_MAX_BITMAP_DIMENSION = 4096
    }
}
