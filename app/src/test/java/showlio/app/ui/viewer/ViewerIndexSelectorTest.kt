package showlio.app.ui.viewer

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerIndexSelectorTest {

    @Test
    fun nextSequentialIndex_wrapsUsingModulo() {
        assertEquals(0, ViewerIndexSelector.nextSequentialIndex(currentIndex = 2, totalCount = 3))
        assertEquals(2, ViewerIndexSelector.nextSequentialIndex(currentIndex = 1, totalCount = 3))
    }

    @Test
    fun isRandomDisplayOrderValid_requiresFullPermutationWithInverseMap() {
        assertTrue(
            ViewerIndexSelector.isRandomDisplayOrderValid(
                order = intArrayOf(2, 0, 1),
                positionByImageIndex = intArrayOf(1, 2, 0),
                totalCount = 3,
                currentOrderPosition = 1
            )
        )
        assertFalse(
            ViewerIndexSelector.isRandomDisplayOrderValid(
                order = intArrayOf(0, 1),
                positionByImageIndex = intArrayOf(0, 1),
                totalCount = 3,
                currentOrderPosition = 0
            )
        )
        assertFalse(
            ViewerIndexSelector.isRandomDisplayOrderValid(
                order = intArrayOf(0, 0, 1),
                positionByImageIndex = intArrayOf(0, 1, 2),
                totalCount = 3,
                currentOrderPosition = 0
            )
        )
    }

    @Test
    fun shouldRebuildRandomOrder_whenCurrentMissingOrAtEnd() {
        assertTrue(ViewerIndexSelector.shouldRebuildRandomOrder(currentOrderPosition = -1, lastOrderPosition = 2))
        assertTrue(ViewerIndexSelector.shouldRebuildRandomOrder(currentOrderPosition = 2, lastOrderPosition = 2))
        assertFalse(ViewerIndexSelector.shouldRebuildRandomOrder(currentOrderPosition = 1, lastOrderPosition = 2))
    }

    @Test
    fun resolveRandomImageIndex_usesCurrentIndexWhenOrderIsEmpty() {
        assertEquals(
            4,
            ViewerIndexSelector.resolveRandomImageIndex(
                order = intArrayOf(),
                nextOrderPosition = 0,
                currentIndex = 4
            )
        )
    }

    @Test
    fun rebuildRandomTraversalState_pinsCurrentImageAtZero() {
        val traversalState = ViewerIndexSelector.rebuildRandomTraversalState(
            totalCount = 6,
            currentIndex = 4,
            randomInt = Random(7)::nextInt
        )

        assertEquals(4, traversalState.order[0])
        assertEquals(0, traversalState.positionByImageIndex[4])
        assertEquals(0, traversalState.currentOrderPosition)
        assertTrue(
            ViewerIndexSelector.isRandomDisplayOrderValid(
                order = traversalState.order,
                positionByImageIndex = traversalState.positionByImageIndex,
                totalCount = 6,
                currentOrderPosition = traversalState.currentOrderPosition
            )
        )
    }

    @Test
    fun randomTraversal_usesDirectPositionAdvancementForLargeSyntheticCounts() {
        val totalCount = 100_000
        val random = Random(11)
        val traversalState = ViewerIndexSelector.rebuildRandomTraversalState(
            totalCount = totalCount,
            currentIndex = 42_000,
            randomInt = random::nextInt
        )

        var currentOrderPosition = traversalState.currentOrderPosition
        var visitedCount = 1
        while (!ViewerIndexSelector.shouldRebuildRandomOrder(currentOrderPosition, traversalState.order.lastIndex)) {
            currentOrderPosition = ViewerIndexSelector.nextRandomOrderPosition(currentOrderPosition)
            val nextImage = ViewerIndexSelector.resolveRandomImageIndex(
                order = traversalState.order,
                nextOrderPosition = currentOrderPosition,
                currentIndex = -1
            )
            assertEquals(currentOrderPosition, traversalState.positionByImageIndex[nextImage])
            visitedCount++
        }

        assertEquals(totalCount, visitedCount)
    }
}
