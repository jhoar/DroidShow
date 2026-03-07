package com.droidshow.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.droidshow.app.databinding.ActivityMainBinding
import com.droidshow.app.ui.viewer.ViewerViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewerViewModel: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val openedUri: Uri? = intent?.data
        viewerViewModel.loadArchiveIfNeeded(openedUri)

        binding.slideshowButton.setOnClickListener {
            viewerViewModel.togglePlayback()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewerViewModel.uiState.collect { state ->
                    val uriText = state.archiveUri?.toString() ?: getString(R.string.no_archive_opened)
                    val positionText = if (state.totalCount > 0) {
                        getString(R.string.slideshow_position, state.currentIndex + 1, state.totalCount)
                    } else {
                        state.errorMessage ?: uriText
                    }
                    binding.openedUriText.text = positionText
                    binding.loadingSpinner.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.slideshowButton.text = if (state.isPlaying) {
                        getString(R.string.pause_slideshow)
                    } else {
                        getString(R.string.start_slideshow)
                    }
                    binding.imageView.setImageBitmap(state.bitmap)
                }
            }
        }
    }
}
