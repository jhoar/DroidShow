package desktopApp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import desktopApp.viewer.DesktopViewerController

@Composable
fun DroidShowDesktopWindow(onCloseRequest: () -> Unit) {
    val controller = remember { DesktopViewerController() }

    Window(onCloseRequest = onCloseRequest, title = "DroidShow Desktop") {
        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("DroidShow Desktop")
                Text("Display mode: ${controller.state.displayMode}")
                Text("Current index: ${controller.state.currentIndex}")
                Button(onClick = { controller.advance() }) {
                    Text("Next")
                }
            }
        }
    }
}
