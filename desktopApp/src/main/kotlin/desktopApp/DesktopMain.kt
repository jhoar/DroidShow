package desktopApp

import androidx.compose.ui.window.application
import desktopApp.ui.DroidShowDesktopWindow
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) = application {
    DroidShowDesktopWindow(
        onCloseRequest = ::exitApplication,
        initialArchivePath = args.firstValidArchivePath()
    )
}

private fun Array<String>.firstValidArchivePath(): String? {
    return firstOrNull { candidate ->
        runCatching {
            val path = Path.of(candidate)
            Files.exists(path) && Files.isRegularFile(path)
        }.getOrDefault(false)
    }
}
