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
        assertValidTraversalState(traversalState, totalCount = 6)
    }

    @Test
    fun rebuildRandomTraversalState_withOutOfBoundsCurrentIndexStillBuildsValidOrder() {
        val traversalState = ViewerIndexSelector.rebuildRandomTraversalState(
            totalCount = 5,
            currentIndex = 42,
            randomInt = Random(3)::nextInt
        )

        assertEquals(0, traversalState.currentOrderPosition)
        assertValidTraversalState(traversalState, totalCount = 5)
    }


    @Test
    fun rebuildRandomTraversalState_neverProducesInvalidStateAcrossSeeds() {
        repeat(64) { seed ->
            val traversalState = ViewerIndexSelector.rebuildRandomTraversalState(
                totalCount = 32,
                currentIndex = seed % 32,
                randomInt = Random(seed)::nextInt
            )
            assertValidTraversalState(traversalState, totalCount = 32)
        }
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
        assertValidTraversalState(traversalState, totalCount)
    }

    private fun assertValidTraversalState(
        traversalState: ViewerIndexSelector.RandomTraversalState,
        totalCount: Int
    ) {
        assertEquals(totalCount, traversalState.order.size)
        assertEquals(totalCount, traversalState.positionByImageIndex.size)
        assertTrue(traversalState.currentOrderPosition in 0 until totalCount)

        val seen = BooleanArray(totalCount)
        for (position in traversalState.order.indices) {
            val imageIndex = traversalState.order[position]
            assertTrue(imageIndex in 0 until totalCount)
            assertFalse(seen[imageIndex])
            seen[imageIndex] = true
            assertEquals(position, traversalState.positionByImageIndex[imageIndex])
        }
    }
}
