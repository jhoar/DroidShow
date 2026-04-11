package desktopApp.archive

import java.io.Closeable
import java.io.InputStream

interface DesktopArchiveReader : Closeable {
    fun listImageEntries(): List<DesktopArchiveEntryRef>

    fun openEntryStream(entry: DesktopArchiveEntryRef): InputStream
}
