package showlio.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
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
    private var pendingArchiveUriForPermission: Uri? = null
    private val readStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pendingUri = pendingArchiveUriForPermission
            pendingArchiveUriForPermission = null
            if (granted && pendingUri != null) {
                viewerViewModel.loadArchiveIfNeeded(pendingUri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureImmersiveMode()
        initializeBinding()
        handleIncomingIntent(intent)
        bindClickListeners()
        observeUiState()
    }

    private fun configureImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun initializeBinding() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySafeTopInset()
    }

    private fun applySafeTopInset() {
        val rootView = binding.root
        val basePaddingTop = rootView.paddingTop

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBarTopInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            val cutoutTopInset = insets.displayCutout?.safeInsetTop ?: 0
            val topInset = MainActivityInsetsPolicy.safeTopInset(
                systemBarTopInset = systemBarTopInset,
                cutoutTopInset = cutoutTopInset
            )
            view.updatePadding(top = basePaddingTop + topInset)
            insets
        }

        ViewCompat.requestApplyInsets(rootView)
    }

    private fun bindClickListeners() {
        binding.openArchiveButton.setOnClickListener {
            archivePicker.launch(ARCHIVE_MIME_TYPES)
        }

        binding.settingsButton.setOnClickListener {
            showSettingsDialog()
        }

        binding.slideshowButton.setOnClickListener {
            viewerViewModel.togglePlayback()
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewerViewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: ViewerUiState) {
        slideshowIntervalSeconds = (state.slideshowIntervalMs / 1_000L).toInt()
        displayMode = state.displayMode
        binding.openedUriText.text = buildPositionText(state)
        renderLoading(state)
        renderPlaybackButton(state)
        binding.imageView.setImageBitmap(state.bitmap)
    }

    private fun buildPositionText(state: ViewerUiState): String {
        val archiveFileName = state.archiveUri?.let { resolveArchiveName(it) }
            ?: getString(R.string.no_archive_opened)
        return MainActivityRenderPolicy.buildPositionText(
            currentIndex = state.currentIndex,
            totalCount = state.totalCount,
            archiveFileName = archiveFileName,
            errorMessage = state.errorMessage
        ) { current, total, fileName ->
            getString(R.string.slideshow_position_with_file, current, total, fileName)
        }
    }

    private fun renderPlaybackButton(state: ViewerUiState) {
        val buttonState = MainActivityRenderPolicy.playbackButtonState(
            totalCount = state.totalCount,
            hasBitmap = state.bitmap != null,
            isLoading = state.isLoading,
            isPlaying = state.isPlaying
        )
        binding.slideshowButton.isEnabled = buttonState.isEnabled
        binding.slideshowButton.setImageDrawable(
            ContextCompat.getDrawable(this@MainActivity, buttonState.iconResId)
        )
        binding.slideshowButton.contentDescription = getString(buttonState.contentDescriptionResId)
    }

    private fun renderLoading(state: ViewerUiState) {
        binding.loadingSpinner.visibility = if (state.isLoading) View.VISIBLE else View.GONE
    }

    private fun showSettingsDialog() {
        val settingsBinding = DialogSettingsBinding.inflate(layoutInflater)
        configureIntervalPicker(
            picker = settingsBinding.slideshowIntervalPicker,
            intervalSeconds = slideshowIntervalSeconds
        )
        settingsBinding.displayModeGroup.check(checkedIdForMode(settingsBinding, displayMode))

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(settingsBinding.root)
            .setPositiveButton(R.string.accept) { _, _ ->
                viewerViewModel.setSlideshowIntervalSeconds(settingsBinding.slideshowIntervalPicker.value)
                viewerViewModel.setDisplayMode(modeFromSelection(settingsBinding))
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
    }

    private fun configureIntervalPicker(picker: NumberPicker, intervalSeconds: Int) {
        picker.minValue = ViewerViewModel.MIN_INTERVAL_SECONDS
        picker.maxValue = ViewerViewModel.MAX_INTERVAL_SECONDS
        picker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        picker.value = intervalSeconds.coerceIn(
            ViewerViewModel.MIN_INTERVAL_SECONDS,
            ViewerViewModel.MAX_INTERVAL_SECONDS
        )
    }

    private fun checkedIdForMode(
        binding: DialogSettingsBinding,
        mode: ViewerUiState.DisplayMode
    ): Int {
        return if (mode == ViewerUiState.DisplayMode.RANDOM) {
            binding.displayModeRandom.id
        } else {
            binding.displayModeSequential.id
        }
    }

    private fun modeFromSelection(binding: DialogSettingsBinding): ViewerUiState.DisplayMode {
        return if (binding.displayModeGroup.checkedRadioButtonId == binding.displayModeRandom.id) {
            ViewerUiState.DisplayMode.RANDOM
        } else {
            ViewerUiState.DisplayMode.SEQUENTIAL
        }
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
        if (requiresLegacyStoragePermission(uri) && !hasLegacyStoragePermission()) {
            pendingArchiveUriForPermission = uri
            readStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }

        viewerViewModel.loadArchiveIfNeeded(uri)
    }

    private fun persistReadPermissionIfPossible(uri: Uri, grantFlags: Int) {
        val hasPersistableGrant = (grantFlags and android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
        if (!hasPersistableGrant) return

        runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun requiresLegacyStoragePermission(uri: Uri): Boolean {
        return uri.scheme == "file" && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
    }

    private fun hasLegacyStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
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
