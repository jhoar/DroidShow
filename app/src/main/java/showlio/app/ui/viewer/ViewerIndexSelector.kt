package showlio.app.ui.viewer

internal object ViewerIndexSelector {

    data class RandomTraversalState(
        val order: IntArray,
        val positionByImageIndex: IntArray,
        val currentOrderPosition: Int
    )

    fun nextSequentialIndex(currentIndex: Int, totalCount: Int): Int = (currentIndex + 1) % totalCount

    fun isRandomDisplayOrderValid(
        order: IntArray,
        positionByImageIndex: IntArray,
        totalCount: Int,
        currentOrderPosition: Int
    ): Boolean {
        if (order.size != totalCount || positionByImageIndex.size != totalCount) return false
        if (totalCount == 0) return currentOrderPosition == -1
        if (currentOrderPosition !in 0 until totalCount) return false

        val seen = BooleanArray(totalCount)
        for (position in order.indices) {
            val imageIndex = order[position]
            if (imageIndex !in 0 until totalCount || seen[imageIndex]) return false
            seen[imageIndex] = true
            if (positionByImageIndex[imageIndex] != position) return false
        }

        return true
    }

    fun rebuildRandomTraversalState(
        totalCount: Int,
        currentIndex: Int,
        randomInt: (bound: Int) -> Int
    ): RandomTraversalState {
        if (totalCount <= 0) {
            return RandomTraversalState(IntArray(0), IntArray(0), -1)
        }

        val order = IntArray(totalCount) { it }
        for (i in order.lastIndex downTo 1) {
            val swapIndex = randomInt(i + 1)
            val tmp = order[i]
            order[i] = order[swapIndex]
            order[swapIndex] = tmp
        }

        val safeCurrentIndex = if (currentIndex in 0 until totalCount) currentIndex else -1
        if (safeCurrentIndex >= 0) {
            val foundPosition = order.indexOfFirst { it == safeCurrentIndex }
            if (foundPosition > 0) {
                order[foundPosition] = order[0]
                order[0] = safeCurrentIndex
            }
        }

        val positionByImageIndex = IntArray(totalCount)
        for (position in order.indices) {
            positionByImageIndex[order[position]] = position
        }

        val currentOrderPosition = if (safeCurrentIndex >= 0) {
            positionByImageIndex[safeCurrentIndex]
        } else {
            0
        }

        return RandomTraversalState(
            order = order,
            positionByImageIndex = positionByImageIndex,
            currentOrderPosition = currentOrderPosition
        )
    }

    fun shouldRebuildRandomOrder(currentOrderPosition: Int, lastOrderPosition: Int): Boolean =
        currentOrderPosition < 0 || currentOrderPosition >= lastOrderPosition

    fun nextRandomOrderPosition(currentOrderPosition: Int): Int = currentOrderPosition + 1

    fun resolveRandomImageIndex(order: IntArray, nextOrderPosition: Int, currentIndex: Int): Int =
        order.getOrElse(nextOrderPosition) { currentIndex }
}
