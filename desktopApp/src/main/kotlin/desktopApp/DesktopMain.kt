package desktopApp

import androidx.compose.ui.window.application
import desktopApp.ui.DroidShowDesktopWindow
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) = application {
    DroidShowDesktopWindow(
        onCloseRequest = ::exitApplication,
        initialArchivePath = args.firstValidArchivePath()
    )
}

private fun Array<String>.firstValidArchivePath(): String? {
    return firstNotNullOfOrNull { candidate ->
        runCatching {
            val path = resolveCandidatePath(candidate)
            if (Files.exists(path) && Files.isRegularFile(path)) {
                path.toString()
            } else {
                null
            }
        }.getOrDefault(null)
    }
}

private fun resolveCandidatePath(candidate: String): Path {
    val sanitizedCandidate = candidate.trim().removeSurrounding("\"")

    return runCatching {
        val uri = URI(sanitizedCandidate)
        if (uri.scheme.equals("file", ignoreCase = true)) {
            Paths.get(uri)
        } else {
            Path.of(sanitizedCandidate)
        }
    }.getOrElse {
        Path.of(sanitizedCandidate)
    }
}
