package showlio.app

internal object MainActivityInsetsPolicy {
    fun safeTopInset(systemBarTopInset: Int, cutoutTopInset: Int): Int {
        return maxOf(systemBarTopInset, cutoutTopInset)
    }
}
