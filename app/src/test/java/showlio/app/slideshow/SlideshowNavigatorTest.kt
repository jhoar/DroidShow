package showlio.app.slideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SlideshowNavigatorTest {

    @Test
    fun sequentialMode_advancesDeterministicallyAndWraps() {
        val navigator = SlideshowNavigator(totalCount = 3, random = Random(0))

        assertEquals(0, navigator.startIndex(SlideshowController.SlideshowMode.SEQUENTIAL))
        assertEquals(1, navigator.nextIndex(SlideshowController.SlideshowMode.SEQUENTIAL))
        assertEquals(2, navigator.nextIndex(SlideshowController.SlideshowMode.SEQUENTIAL))
        assertEquals(0, navigator.nextIndex(SlideshowController.SlideshowMode.SEQUENTIAL))
    }

    @Test
    fun randomMode_doesNotRepeatUntilBagExhausted() {
        val navigator = SlideshowNavigator(totalCount = 4, random = Random(42))

        val seen = mutableSetOf<Int>()
        val first = navigator.startIndex(SlideshowController.SlideshowMode.RANDOM)
        seen += first

        repeat(3) {
            seen += navigator.nextIndex(SlideshowController.SlideshowMode.RANDOM)
        }

        assertEquals(setOf(0, 1, 2, 3), seen)
    }

    @Test
    fun previous_movesBackThroughHistory_andNextRestoresForwardHistory() {
        val navigator = SlideshowNavigator(totalCount = 5, random = Random(0))

        navigator.startIndex(SlideshowController.SlideshowMode.SEQUENTIAL) // 0
        navigator.nextIndex(SlideshowController.SlideshowMode.SEQUENTIAL) // 1
        navigator.nextIndex(SlideshowController.SlideshowMode.SEQUENTIAL) // 2

        assertEquals(1, navigator.previousIndex())
        assertEquals(0, navigator.previousIndex())
        assertNull(navigator.previousIndex())

        assertEquals(1, navigator.nextIndex(SlideshowController.SlideshowMode.SEQUENTIAL))
        assertEquals(2, navigator.nextIndex(SlideshowController.SlideshowMode.SEQUENTIAL))
    }

    @Test
    fun changingToRandomMode_refillsBagExcludingCurrentEntryWhenPossible() {
        val navigator = SlideshowNavigator(totalCount = 3, random = Random(9))

        navigator.startIndex(SlideshowController.SlideshowMode.SEQUENTIAL) // 0
        navigator.onModeChanged(SlideshowController.SlideshowMode.RANDOM)

        val next = navigator.nextIndex(SlideshowController.SlideshowMode.RANDOM)
        assertTrue(next != 0)
    }

    @Test(expected = IllegalStateException::class)
    fun startIndex_throwsWhenNoEntries() {
        val navigator = SlideshowNavigator(totalCount = 0, random = Random(1))

        navigator.startIndex(SlideshowController.SlideshowMode.SEQUENTIAL)
    }

    @Test(expected = IllegalStateException::class)
    fun nextIndex_throwsWhenNoEntries() {
        val navigator = SlideshowNavigator(totalCount = 0, random = Random(1))

        navigator.nextIndex(SlideshowController.SlideshowMode.SEQUENTIAL)
    }

    @Test
    fun randomMode_singleEntryAlwaysReturnsZero() {
        val navigator = SlideshowNavigator(totalCount = 1, random = Random(7))

        assertEquals(0, navigator.startIndex(SlideshowController.SlideshowMode.RANDOM))
        assertEquals(0, navigator.nextIndex(SlideshowController.SlideshowMode.RANDOM))
        assertEquals(0, navigator.nextIndex(SlideshowController.SlideshowMode.RANDOM))
    }

    @Test
    fun switchingBackToSequentialClearsRandomBagAndContinuesSequentially() {
        val navigator = SlideshowNavigator(totalCount = 4, random = Random(33))

        navigator.startIndex(SlideshowController.SlideshowMode.SEQUENTIAL) // 0
        navigator.onModeChanged(SlideshowController.SlideshowMode.RANDOM)
        navigator.nextIndex(SlideshowController.SlideshowMode.RANDOM)

        navigator.onModeChanged(SlideshowController.SlideshowMode.SEQUENTIAL)
        val next = navigator.nextIndex(SlideshowController.SlideshowMode.SEQUENTIAL)

        assertEquals(2, next)
    }
}
