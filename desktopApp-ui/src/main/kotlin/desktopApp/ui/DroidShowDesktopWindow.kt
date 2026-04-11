package desktopApp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import desktopApp.policy.ViewerDisplayMode
import desktopApp.viewer.DesktopViewerController
import desktopApp.viewer.ViewerStatePolicy
import desktopApp.viewer.ViewerUiState
import java.awt.FileDialog
import java.io.File

@Composable
fun DroidShowDesktopWindow(onCloseRequest: () -> Unit) {
    val controller = remember { DesktopViewerController() }

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    Window(
        onCloseRequest = {
            controller.close()
            onCloseRequest()
        },
        title = "Showlio",
        icon = painterResource("showlio-icon.svg")
    ) {
        val state by controller.uiState.collectAsState()
        var showSettings by remember { mutableStateOf(false) }

        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openArchivePicker(window) { controller.loadArchive(it) } }) {
                        Text("Open Archive")
                    }
                    Button(
                        enabled = canControlPlayback(state),
                        onClick = { controller.togglePlayback() }
                    ) {
                        Text(if (state.isPlaying) "Pause" else "Play")
                    }
                    Button(onClick = { showSettings = true }) {
                        Text("Settings")
                    }
                }

                Text(buildStatusText(state))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = state.bitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Archive image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = if (state.isLoading) "Loading..." else "No image",
                            color = Color.White
                        )
                    }
                }
            }

            if (showSettings) {
                SettingsDialog(
                    initialIntervalSeconds = (state.slideshowIntervalMs / 1_000L).toInt(),
                    initialDisplayMode = state.displayMode,
                    onDismiss = { showSettings = false },
                    onApply = { intervalSeconds, displayMode ->
                        controller.setSlideshowIntervalSeconds(intervalSeconds)
                        controller.setDisplayMode(displayMode)
                        showSettings = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    initialIntervalSeconds: Int,
    initialDisplayMode: ViewerDisplayMode,
    onDismiss: () -> Unit,
    onApply: (Int, ViewerDisplayMode) -> Unit
) {
    var intervalSeconds by remember(initialIntervalSeconds) { mutableFloatStateOf(initialIntervalSeconds.toFloat()) }
    var selectedMode by remember(initialDisplayMode) { mutableStateOf(initialDisplayMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Slideshow interval: ${intervalSeconds.toInt()} sec")
                Slider(
                    value = intervalSeconds,
                    valueRange = ViewerStatePolicy.MIN_INTERVAL_SECONDS.toFloat()..
                        ViewerStatePolicy.MAX_INTERVAL_SECONDS.toFloat(),
                    onValueChange = { intervalSeconds = it },
                    steps = ViewerStatePolicy.MAX_INTERVAL_SECONDS - ViewerStatePolicy.MIN_INTERVAL_SECONDS - 1
                )

                Text("Display mode")
                DisplayModeOption(
                    label = "Sequential",
                    selected = selectedMode == ViewerDisplayMode.SEQUENTIAL,
                    onSelected = { selectedMode = ViewerDisplayMode.SEQUENTIAL }
                )
                DisplayModeOption(
                    label = "Random",
                    selected = selectedMode == ViewerDisplayMode.RANDOM,
                    onSelected = { selectedMode = ViewerDisplayMode.RANDOM }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(intervalSeconds.toInt(), selectedMode) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DisplayModeOption(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .selectable(selected = selected, onClick = onSelected),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelected)
        Text(label)
    }
}

private fun canControlPlayback(state: ViewerUiState): Boolean {
    return state.totalCount > 0 && state.bitmap != null && !state.isLoading
}

private fun buildStatusText(state: ViewerUiState): String {
    val archiveName = state.archivePath?.let { path ->
        File(path).name.ifBlank { path }
    } ?: "No archive opened"

    val base = if (state.totalCount > 0) {
        "${state.currentIndex + 1}/${state.totalCount} • $archiveName"
    } else {
        archiveName
    }

    val error = state.errorMessage
    return if (error.isNullOrBlank()) base else "$base • $error"
}

private fun openArchivePicker(window: androidx.compose.ui.awt.ComposeWindow, onSelected: (String) -> Unit) {
    val picker = FileDialog(window, "Open Archive", FileDialog.LOAD).apply {
        file = "*.zip"
        isVisible = true
    }
    val selectedFile = picker.file ?: return
    onSelected(File(picker.directory, selectedFile).absolutePath)
}
