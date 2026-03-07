package showlio.app.archive

import org.junit.Assert.assertNotNull
import org.junit.Test

class SevenZDependenciesTest {

    @Test
    fun `xz dependency is available for 7z decoding`() {
        val filterOptionsClass = Class.forName("org.tukaani.xz.FilterOptions")
        assertNotNull(filterOptionsClass)
    }
}
