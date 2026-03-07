package com.droidshow.app.archive

import java.util.Locale

internal object ArchiveEntrySupport {
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heif", "heic"
    )

    val naturalPathComparator = Comparator<String> { left, right ->
        naturalCompare(left, right)
    }

    fun isImageEntry(path: String): Boolean {
        val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.US)
        return extension in imageExtensions
    }

    private fun naturalCompare(left: String, right: String): Int {
        var i = 0
        var j = 0
        while (i < left.length && j < right.length) {
            val leftChar = left[i]
            val rightChar = right[j]

            if (leftChar.isDigit() && rightChar.isDigit()) {
                val leftStart = i
                val rightStart = j

                while (i < left.length && left[i].isDigit()) i++
                while (j < right.length && right[j].isDigit()) j++

                val leftNumber = left.substring(leftStart, i).trimStart('0')
                val rightNumber = right.substring(rightStart, j).trimStart('0')

                if (leftNumber.length != rightNumber.length) {
                    return leftNumber.length - rightNumber.length
                }

                val numberComparison = leftNumber.compareTo(rightNumber)
                if (numberComparison != 0) return numberComparison

                val rawLengthComparison = (i - leftStart) - (j - rightStart)
                if (rawLengthComparison != 0) return rawLengthComparison
            } else {
                val comparison = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
                if (comparison != 0) return comparison
                i++
                j++
            }
        }
        return left.length - right.length
    }
}
