package com.droidshow.app.ui.viewer

import android.graphics.Bitmap
import android.net.Uri

data class ViewerUiState(
    val archiveUri: Uri? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val bitmap: Bitmap? = null,
    val errorMessage: String? = null
)
