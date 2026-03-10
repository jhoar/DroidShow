package showlio.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityIntentFiltersTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun resolvesContentArchiveWhenMimeTypeIsExplicit() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(Uri.parse("content://provider/docs/archive.cbz"), "application/vnd.comicbook+zip")
            setPackage(context.packageName)
        }

        assertTrue(resolvesToMainActivity(intent))
    }

    @Test
    fun resolvesContentArchiveByExtensionWhenMimeTypeMissing() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("content://provider/docs/archive.cbr")
            setPackage(context.packageName)
        }

        assertTrue(resolvesToMainActivity(intent))
    }

    @Test
    fun resolvesFileSchemeArchiveByExtensionFallback() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("file:///sdcard/Download/archive.cbz")
            setPackage(context.packageName)
        }

        assertTrue(resolvesToMainActivity(intent))
    }


    @Test
    fun resolvesContentArchiveWhenMimeTypeIsGeneric() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(Uri.parse("content://provider/docs/archive.rar"), "application/octet-stream")
            setPackage(context.packageName)
        }

        assertTrue(resolvesToMainActivity(intent))
    }

    @Test
    fun resolvesWildcardMimeForKnownArchiveType() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(Uri.parse("content://provider/docs/archive.zip"), "*/*")
            setPackage(context.packageName)
        }

        // Android's intent resolution treats */* as compatible with concrete MIME filters,
        // so this still resolves via the explicit archive MIME declarations.
        assertTrue(resolvesToMainActivity(intent))
    }

    @Test
    fun doesNotResolveUnknownTypeWithUnknownExtension() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(Uri.parse("content://provider/docs/readme.txt"), "text/plain")
            setPackage(context.packageName)
        }

        assertFalse(resolvesToMainActivity(intent))
    }

    @Test
    fun resolvesBrowsableEntry() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setDataAndType(Uri.parse("content://provider/docs/archive.zip"), "application/zip")
            setPackage(context.packageName)
        }

        assertTrue(resolvesToMainActivity(intent))
    }

    @Test
    fun resolvesUppercaseExtensionFallback() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("content://provider/docs/archive.RAR")
            setPackage(context.packageName)
        }

        assertTrue(resolvesToMainActivity(intent))
    }

    private fun resolvesToMainActivity(intent: Intent): Boolean {
        val matches = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return matches.any { it.activityInfo?.name == MainActivity::class.java.name }
    }
}
