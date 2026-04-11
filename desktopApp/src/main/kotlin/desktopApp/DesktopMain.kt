package desktopApp

import androidx.compose.ui.window.application
import desktopApp.ui.DroidShowDesktopWindow

fun main() = application {
    DroidShowDesktopWindow(onCloseRequest = ::exitApplication)
}
