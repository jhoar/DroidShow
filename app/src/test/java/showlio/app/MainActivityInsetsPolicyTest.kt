package showlio.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityInsetsPolicyTest {
    @Test
    fun `safeTopInset returns system bar inset when it is larger`() {
        val result = MainActivityInsetsPolicy.safeTopInset(systemBarTopInset = 48, cutoutTopInset = 24)

        assertEquals(48, result)
    }

    @Test
    fun `safeTopInset returns cutout inset when it is larger`() {
        val result = MainActivityInsetsPolicy.safeTopInset(systemBarTopInset = 24, cutoutTopInset = 48)

        assertEquals(48, result)
    }

    @Test
    fun `safeTopInset returns zero when both insets are zero`() {
        val result = MainActivityInsetsPolicy.safeTopInset(systemBarTopInset = 0, cutoutTopInset = 0)

        assertEquals(0, result)
    }
}
