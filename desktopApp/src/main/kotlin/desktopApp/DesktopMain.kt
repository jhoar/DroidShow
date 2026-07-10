package desktopApp

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.application
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import desktopApp.ui.DroidShowDesktopWindow
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) = application {
    var archivePath by remember { mutableStateOf(resolveProcessArgs(args).firstValidArchivePath()) }

    DisposableEffect(Unit) {
        val desktop = Desktop.getDesktop().takeIf {
            Desktop.isDesktopSupported() && it.isSupported(Desktop.Action.APP_OPEN_FILE)
        }
        desktop?.setOpenFileHandler { event ->
            event.files.firstOrNull()?.path?.let { archivePath = it }
        }
        onDispose { desktop?.setOpenFileHandler(null) }
    }

    DroidShowDesktopWindow(
        onCloseRequest = ::exitApplication,
        initialArchivePath = archivePath
    )
}

/**
 * On Windows, argv delivered to `main()` is decoded using the process's ANSI code page
 * (sun.jnu.encoding), which mangles file names containing characters outside that code page
 * (e.g. a Japanese-named archive opened via "Open with" on an English-locale Windows install).
 * The character count/positions are still correct, so we recover the true Unicode arguments
 * straight from the OS via GetCommandLineW/CommandLineToArgvW and take the trailing N of them,
 * where N is the (possibly mangled) arg count reported by the JVM.
 */
internal fun resolveProcessArgs(jvmArgs: Array<String>): Array<String> {
    if (jvmArgs.isEmpty() || !isWindows()) return jvmArgs
    val rawArgs = windowsCommandLineArgs() ?: return jvmArgs
    if (rawArgs.size < jvmArgs.size) return jvmArgs
    return rawArgs.takeLast(jvmArgs.size).toTypedArray()
}

private fun isWindows(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().contains("windows")

private interface Kernel32Lib : Library {
    fun GetCommandLineW(): WString
    fun LocalFree(hMem: Pointer): Pointer
}

private interface Shell32Lib : Library {
    fun CommandLineToArgvW(cmdLine: WString, numArgs: IntByReference): Pointer?
}

/** Returns the process's real Unicode command-line arguments (excluding argv[0]), or null on failure. */
internal fun windowsCommandLineArgs(): List<String>? = runCatching {
    val kernel32 = Native.load("kernel32", Kernel32Lib::class.java)
    val shell32 = Native.load("shell32", Shell32Lib::class.java)

    val argc = IntByReference()
    val argv = shell32.CommandLineToArgvW(kernel32.GetCommandLineW(), argc)
        ?: throw IllegalStateException("CommandLineToArgvW returned null")
    try {
        (1 until argc.value).map { i ->
            argv.getPointer(i.toLong() * Native.POINTER_SIZE).getWideString(0)
        }
    } finally {
        kernel32.LocalFree(argv)
    }
}.getOrNull()

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
