package showlio.app.ui.viewer

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
    fun isRandomDisplayOrderValid_requiresFullPermutation() {
        assertTrue(ViewerIndexSelector.isRandomDisplayOrderValid(listOf(2, 0, 1), totalCount = 3))
        assertFalse(ViewerIndexSelector.isRandomDisplayOrderValid(listOf(0, 1), totalCount = 3))
        assertFalse(ViewerIndexSelector.isRandomDisplayOrderValid(listOf(0, 0, 1), totalCount = 3))
    }

    @Test
    fun shouldRebuildRandomOrder_whenCurrentMissingOrAtEnd() {
        assertTrue(ViewerIndexSelector.shouldRebuildRandomOrder(currentOrderPosition = -1, lastOrderPosition = 2))
        assertTrue(ViewerIndexSelector.shouldRebuildRandomOrder(currentOrderPosition = 2, lastOrderPosition = 2))
        assertFalse(ViewerIndexSelector.shouldRebuildRandomOrder(currentOrderPosition = 1, lastOrderPosition = 2))
    }

    @Test
    fun resolveRandomImageIndex_clampsToLastValidOrderPosition() {
        val order = listOf(2)

        assertEquals(
            2,
            ViewerIndexSelector.resolveRandomImageIndex(
                displayOrder = order,
                nextOrderPosition = 1,
                currentIndex = 4
            )
        )
    }


    @Test
    fun resolveRandomImageIndex_usesCurrentIndexWhenOrderIsEmpty() {
        assertEquals(
            4,
            ViewerIndexSelector.resolveRandomImageIndex(
                displayOrder = emptyList(),
                nextOrderPosition = 0,
                currentIndex = 4
            )
        )
    }

    @Test
    fun randomProgression_helpersAdvanceWithinOrder() {
        val order = listOf(1, 0, 2)
        val currentOrderPosition = ViewerIndexSelector.currentRandomOrderPosition(order, currentIndex = 0)
        val nextOrderPosition = ViewerIndexSelector.nextRandomOrderPosition(currentOrderPosition)

        assertEquals(1, currentOrderPosition)
        assertEquals(2, nextOrderPosition)
        assertEquals(2, ViewerIndexSelector.resolveRandomImageIndex(order, nextOrderPosition, currentIndex = 0))
    }
}
