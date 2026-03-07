package showlio.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.NumberPicker
import android.provider.OpenableColumns
import showlio.app.ui.viewer.ViewerUiState
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import showlio.app.databinding.ActivityMainBinding
import showlio.app.databinding.DialogSettingsBinding
import showlio.app.ui.viewer.ViewerViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewerViewModel: ViewerViewModel by viewModels()
    private var slideshowIntervalSeconds: Int = DEFAULT_SLIDESHOW_INTERVAL_SECONDS
    private var displayMode: ViewerUiState.DisplayMode = ViewerUiState.DisplayMode.SEQUENTIAL
    private val archivePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        persistReadPermissionIfPossible(
            uri = uri,
            grantFlags = android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        viewerViewModel.loadArchiveIfNeeded(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleIncomingIntent(intent)

        binding.openArchiveButton.setOnClickListener {
            archivePicker.launch(ARCHIVE_MIME_TYPES)
        }

        binding.settingsButton.setOnClickListener {
            showSettingsDialog()
        }

        binding.slideshowButton.setOnClickListener {
            viewerViewModel.togglePlayback()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewerViewModel.uiState.collect { state ->
                    slideshowIntervalSeconds = (state.slideshowIntervalMs / 1_000L).toInt()
                    displayMode = state.displayMode
                    val archiveFileName = state.archiveUri?.let { resolveArchiveName(it) }
                        ?: getString(R.string.no_archive_opened)
                    val positionText = if (state.totalCount > 0) {
                        getString(
                            R.string.slideshow_position_with_file,
                            state.currentIndex + 1,
                            state.totalCount,
                            archiveFileName
                        )
                    } else {
                        state.errorMessage ?: archiveFileName
                    }
                    binding.openedUriText.text = positionText
                    binding.loadingSpinner.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    val canControlSlideshow = state.totalCount > 0 && state.bitmap != null && !state.isLoading
                    binding.slideshowButton.isEnabled = canControlSlideshow
                    if (state.isPlaying) {
                        binding.slideshowButton.setImageDrawable(
                            ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_media_pause)
                        )
                        binding.slideshowButton.contentDescription = getString(R.string.pause_slideshow)
                    } else {
                        binding.slideshowButton.setImageDrawable(
                            ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_media_play)
                        )
                        binding.slideshowButton.contentDescription = getString(R.string.start_slideshow)
                    }
                    binding.imageView.setImageBitmap(state.bitmap)
                }
            }
        }
    }

    private fun showSettingsDialog() {
        val settingsBinding = DialogSettingsBinding.inflate(layoutInflater)
        settingsBinding.slideshowIntervalPicker.apply {
            minValue = ViewerViewModel.MIN_INTERVAL_SECONDS
            maxValue = ViewerViewModel.MAX_INTERVAL_SECONDS
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            value = slideshowIntervalSeconds.coerceIn(minValue, maxValue)
        }

        val checkedModeId = if (displayMode == ViewerUiState.DisplayMode.RANDOM) {
            settingsBinding.displayModeRandom.id
        } else {
            settingsBinding.displayModeSequential.id
        }
        settingsBinding.displayModeGroup.check(checkedModeId)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(settingsBinding.root)
            .setPositiveButton(R.string.accept) { _, _ ->
                viewerViewModel.setSlideshowIntervalSeconds(settingsBinding.slideshowIntervalPicker.value)
                val selectedMode = if (settingsBinding.displayModeGroup.checkedRadioButtonId == settingsBinding.displayModeRandom.id) {
                    ViewerUiState.DisplayMode.RANDOM
                } else {
                    ViewerUiState.DisplayMode.SEQUENTIAL
                }
                viewerViewModel.setDisplayMode(selectedMode)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_VIEW) {
            viewerViewModel.loadArchiveIfNeeded(intent?.data)
            return
        }

        val uri = intent.data ?: return
        persistReadPermissionIfPossible(uri = uri, grantFlags = intent.flags)
        viewerViewModel.loadArchiveIfNeeded(uri)
    }

    private fun persistReadPermissionIfPossible(uri: Uri, grantFlags: Int) {
        val hasPersistableGrant = (grantFlags and android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
        if (!hasPersistableGrant) return

        runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun resolveArchiveName(uri: Uri): String {
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
            ?: getString(R.string.unknown_archive_name)

        val displayName = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex == -1) null else cursor.getString(nameIndex)
            }
        }.getOrNull()

        val resolvedName = displayName?.ifBlank { null } ?: fallbackName
        return truncateArchiveName(resolvedName)
    }

    private fun truncateArchiveName(fileName: String): String {
        if (fileName.length <= ARCHIVE_NAME_MAX_LENGTH) return fileName
        return fileName.take(ARCHIVE_NAME_MAX_LENGTH - ELLIPSIS.length) + ELLIPSIS
    }

    companion object {
        private const val DEFAULT_SLIDESHOW_INTERVAL_SECONDS = 3
        private const val ARCHIVE_NAME_MAX_LENGTH = 40
        private const val ELLIPSIS = "…"
        private val ARCHIVE_MIME_TYPES = arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/vnd.comicbook+zip",
            "application/x-cbz",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/vnd.comicbook-rar",
            "application/x-cbr",
            "application/x-7z-compressed",
            "application/x-cb7"
        )
    }
}
