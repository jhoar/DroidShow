package desktopApp.viewer

import androidx.compose.ui.graphics.ImageBitmap
import desktopApp.archive.DesktopArchiveEntryRef
import desktopApp.archive.DesktopArchiveReader
import desktopApp.policy.ViewerDisplayMode
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerViewModelTest {

    @Test
    fun intervalIsClampedToOneAndThirtySeconds() {
        val vm = createViewModel()

        vm.setSlideshowIntervalSeconds(0)
        assertEquals(1_000L, vm.uiState.value.slideshowIntervalMs)

        vm.setSlideshowIntervalSeconds(99)
        assertEquals(30_000L, vm.uiState.value.slideshowIntervalMs)

        vm.close()
    }

    @Test
    fun loadArchiveStopsPlaybackWhenArchiveChanges() = runBlocking {
        val vm = createViewModel()
        vm.loadArchiveIfNeeded("/tmp/a.cbz")
        delay(20)
        vm.togglePlayback()
        assertTrue(vm.uiState.value.isPlaying)

        vm.loadArchiveIfNeeded("/tmp/b.cbz")
        assertFalse(vm.uiState.value.isPlaying)
        vm.close()
    }

    @Test
    fun duplicateLoadsAreGuarded() = runBlocking {
        val createCalls = AtomicInteger(0)
        val vm = createViewModel(readerFactory = {
            createCalls.incrementAndGet()
            FakeReader(entriesFor(it))
        })

        vm.loadArchiveIfNeeded("/tmp/a.cbz")
        delay(20)
        vm.loadArchiveIfNeeded("/tmp/a.cbz")
        delay(20)

        assertEquals(1, createCalls.get())
        vm.close()
    }

    @Test
    fun randomTraversalRebuildsAtCycleBoundaryAndKeepsPermutation() = runBlocking {
        val vm = createViewModel(random = sequenceRng(0, 1, 2, 3, 0, 1, 2, 3, 0))
        vm.loadArchiveIfNeeded("/tmp/a.cbz")
        delay(20)
        vm.setDisplayMode(ViewerDisplayMode.RANDOM)

        val seen = linkedSetOf(vm.uiState.value.currentIndex)
        repeat(vm.uiState.value.totalCount - 1) {
            vm.advanceOnce()
            seen += vm.uiState.value.currentIndex
        }
        assertEquals(vm.uiState.value.totalCount, seen.size)

        val firstCycleLast = vm.uiState.value.currentIndex
        vm.advanceOnce()
        val nextCycleFirst = vm.uiState.value.currentIndex

        assertTrue(nextCycleFirst in 0 until vm.uiState.value.totalCount)
        assertTrue(firstCycleLast in 0 until vm.uiState.value.totalCount)
        vm.close()
    }

    @Test
    fun restoreUsesPersistenceAdapter() = runBlocking {
        val persistence = InMemoryPersistence(
            ViewerPersistedState(
                archivePath = "/tmp/a.cbz",
                currentIndex = 2,
                isPlaying = false,
                slideshowIntervalMs = 90_000L,
                displayMode = ViewerDisplayMode.SEQUENTIAL
            )
        )

        val vm = createViewModel(persistence = persistence)
        delay(20)

        assertEquals("/tmp/a.cbz", vm.uiState.value.archivePath)
        assertEquals(2, vm.uiState.value.currentIndex)
        assertEquals(30_000L, vm.uiState.value.slideshowIntervalMs)
        vm.close()
    }

    @Test
    fun decodeFailureClearsPreviousBitmapAndShowsMessage() = runBlocking {
        val vm = createViewModel(
            readerFactory = { FailingOnSecondEntryReader(entriesFor("/tmp/a.cbz")) },
            decoder = RecordingDecoder()
        )

        vm.loadArchiveIfNeeded("/tmp/a.cbz")
        delay(80)
        assertNotNull(vm.uiState.value.bitmap)

        vm.advanceOnce()
        delay(80)

        assertNull(vm.uiState.value.bitmap)
        assertEquals("Archive appears to be corrupt.", vm.uiState.value.errorMessage)
        vm.close()
    }

    @Test
    fun latestPageDecodeWinsWhenUserSkipsQuickly() = runBlocking {
        val decoder = DelayedByMarkerDecoder(firstDelayMs = 120L, secondDelayMs = 10L)
        val vm = createViewModel(
            readerFactory = { MarkerStreamReader(entriesFor("/tmp/a.cbz")) },
            decoder = decoder
        )

        vm.loadArchiveIfNeeded("/tmp/a.cbz")
        delay(10)
        vm.advanceOnce()
        delay(180)

        assertEquals(1, vm.uiState.value.currentIndex)
        assertEquals(1, decoder.lastDecodedMarker)
        vm.close()
    }

    private fun createViewModel(
        persistence: ViewerPersistence = InMemoryPersistence(),
        readerFactory: (String) -> DesktopArchiveReader = { FakeReader(entriesFor(it)) },
        decoder: ArchiveImageDecoder = RecordingDecoder(),
        random: (Int) -> Int = { 0 }
    ): ViewerViewModel {
        return ViewerViewModel(
            persistence = persistence,
            readerFactory = readerFactory,
            imageDecoder = decoder,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            randomInt = random
        )
    }

    private fun entriesFor(path: String): List<DesktopArchiveEntryRef> {
        return List(4) { idx ->
            DesktopArchiveEntryRef(Path.of(path), "image_$idx.jpg", 0, 0, idx)
        }
    }

    private fun sequenceRng(vararg values: Int): (Int) -> Int {
        val sequence = values.iterator()
        return { bound ->
            val next = if (sequence.hasNext()) sequence.nextInt() else 0
            next % bound
        }
    }

    private class InMemoryPersistence(
        initial: ViewerPersistedState? = null
    ) : ViewerPersistence {
        var value: ViewerPersistedState? = initial

        override fun load(): ViewerPersistedState? = value

        override fun save(state: ViewerPersistedState) {
            value = state
        }
    }

    private class FakeReader(
        private val entries: List<DesktopArchiveEntryRef>
    ) : DesktopArchiveReader {
        override fun listImageEntries(): List<DesktopArchiveEntryRef> = entries

        override fun openEntryStream(entry: DesktopArchiveEntryRef) = ByteArrayInputStream(byteArrayOf(entry.index.toByte()))

        override fun close() = Unit
    }

    private class FailingOnSecondEntryReader(
        private val entries: List<DesktopArchiveEntryRef>
    ) : DesktopArchiveReader {
        override fun listImageEntries(): List<DesktopArchiveEntryRef> = entries

        override fun openEntryStream(entry: DesktopArchiveEntryRef): ByteArrayInputStream {
            if (entry.index == 1) throw IOException("corrupt")
            return ByteArrayInputStream(byteArrayOf(entry.index.toByte()))
        }

        override fun close() = Unit
    }

    private class MarkerStreamReader(
        private val entries: List<DesktopArchiveEntryRef>
    ) : DesktopArchiveReader {
        override fun listImageEntries(): List<DesktopArchiveEntryRef> = entries

        override fun openEntryStream(entry: DesktopArchiveEntryRef): ByteArrayInputStream {
            return ByteArrayInputStream(byteArrayOf(entry.index.toByte()))
        }

        override fun close() = Unit
    }

    private class RecordingDecoder : ArchiveImageDecoder {
        override fun decode(stream: java.io.InputStream, maxDimension: Int): ImageBitmap = ImageBitmap(1, 1)
    }

    private class DelayedByMarkerDecoder(
        private val firstDelayMs: Long,
        private val secondDelayMs: Long
    ) : ArchiveImageDecoder {
        @Volatile
        var lastDecodedMarker: Int = -1

        override fun decode(stream: java.io.InputStream, maxDimension: Int): ImageBitmap {
            val marker = stream.read()
            if (marker == 0) Thread.sleep(firstDelayMs) else Thread.sleep(secondDelayMs)
            lastDecodedMarker = marker
            return ImageBitmap(1, 1)
        }
    }
}
