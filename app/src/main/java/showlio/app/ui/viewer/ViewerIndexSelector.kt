package showlio.app.ui.viewer

internal object ViewerIndexSelector {

    fun nextSequentialIndex(currentIndex: Int, totalCount: Int): Int = (currentIndex + 1) % totalCount

    fun isRandomDisplayOrderValid(displayOrder: List<Int>, totalCount: Int): Boolean {
        if (displayOrder.size != totalCount) return false
        return displayOrder.toSet() == (0 until totalCount).toSet()
    }

    fun currentRandomOrderPosition(displayOrder: List<Int>, currentIndex: Int): Int =
        displayOrder.indexOf(currentIndex)

    fun shouldRebuildRandomOrder(currentOrderPosition: Int, lastOrderPosition: Int): Boolean =
        currentOrderPosition < 0 || currentOrderPosition >= lastOrderPosition

    fun nextRandomOrderPosition(currentOrderPosition: Int): Int = currentOrderPosition + 1

    fun resolveRandomImageIndex(
        displayOrder: List<Int>,
        nextOrderPosition: Int,
        currentIndex: Int
    ): Int {
        val safeOrderPosition = nextOrderPosition.coerceAtMost(displayOrder.lastIndex)
        return displayOrder.getOrElse(safeOrderPosition) { currentIndex }
    }
}
