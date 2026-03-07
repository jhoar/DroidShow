package com.droidshow.app.slideshow

import kotlin.random.Random

internal class SlideshowNavigator(
    private val totalCount: Int,
    private val random: Random
) {
    private val history = mutableListOf<Int>()
    private var historyPointer = -1
    private val randomBag = ArrayDeque<Int>()

    fun currentIndex(): Int? = history.getOrNull(historyPointer)

    fun hasEntries(): Boolean = totalCount > 0

    fun startIndex(mode: SlideshowController.SlideshowMode): Int {
        if (!hasEntries()) {
            throw IllegalStateException("Cannot start slideshow without entries")
        }

        val initial = if (mode == SlideshowController.SlideshowMode.SEQUENTIAL) {
            0
        } else {
            nextRandomIndex(currentIndex = null)
        }

        push(initial)
        return initial
    }

    fun nextIndex(mode: SlideshowController.SlideshowMode): Int {
        if (!hasEntries()) {
            throw IllegalStateException("Cannot advance slideshow without entries")
        }

        if (historyPointer < history.lastIndex) {
            historyPointer += 1
            return history[historyPointer]
        }

        val current = currentIndex()
        val next = when (mode) {
            SlideshowController.SlideshowMode.SEQUENTIAL -> nextSequentialIndex(current)
            SlideshowController.SlideshowMode.RANDOM -> nextRandomIndex(current)
        }

        push(next)
        return next
    }

    fun previousIndex(): Int? {
        if (historyPointer <= 0) {
            return null
        }

        historyPointer -= 1
        return history[historyPointer]
    }

    fun onModeChanged(mode: SlideshowController.SlideshowMode) {
        if (mode == SlideshowController.SlideshowMode.RANDOM) {
            refillRandomBag(excluding = currentIndex())
        } else {
            randomBag.clear()
        }
    }

    private fun push(index: Int) {
        if (historyPointer < history.lastIndex) {
            history.subList(historyPointer + 1, history.size).clear()
        }

        history += index
        historyPointer = history.lastIndex
    }

    private fun nextSequentialIndex(currentIndex: Int?): Int {
        if (currentIndex == null) return 0
        return (currentIndex + 1) % totalCount
    }

    private fun nextRandomIndex(currentIndex: Int?): Int {
        if (totalCount <= 1) return 0

        if (randomBag.isEmpty()) {
            refillRandomBag(excluding = currentIndex)
        }

        return randomBag.removeFirst()
    }

    private fun refillRandomBag(excluding: Int?) {
        randomBag.clear()

        val shuffled = (0 until totalCount).shuffled(random)
        for (candidate in shuffled) {
            if (excluding != null && totalCount > 1 && candidate == excluding) {
                continue
            }

            randomBag.addLast(candidate)
        }
    }
}
