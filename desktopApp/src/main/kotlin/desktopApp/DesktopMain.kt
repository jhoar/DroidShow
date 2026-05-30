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
        // URI(String) rejects non-ASCII characters (e.g. Japanese filenames). When the
        // candidate looks like a file:// URL, re-extract the path and rebuild a
        // properly-encoded URI; otherwise fall back to treating it as a plain path.
        if (sanitizedCandidate.startsWith("file:", ignoreCase = true)) {
            tryResolveFileUrl(sanitizedCandidate) ?: Path.of(sanitizedCandidate)
        } else {
            Path.of(sanitizedCandidate)
        }
    }
}

internal fun tryResolveFileUrl(fileUrl: String): Path? = runCatching {
    val afterScheme = fileUrl.substring("file:".length)
    val rawPath = when {
        afterScheme.startsWith("//") -> {
            // file://[host]/path — skip optional authority and keep the path
            val afterSlashes = afterScheme.substring(2)
            val pathStart = afterSlashes.indexOf('/')
            if (pathStart >= 0) afterSlashes.substring(pathStart) else "/"
        }
        else -> afterScheme
    }
    // URI(scheme, authority, path, query, fragment) percent-encodes non-ASCII characters
    // in path, producing a valid URI that Paths.get(URI) can resolve correctly.
    Paths.get(URI("file", "", rawPath, null, null))
}.getOrNull()
