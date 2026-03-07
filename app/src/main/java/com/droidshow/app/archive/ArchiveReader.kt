package com.droidshow.app.archive

import java.io.Closeable
import java.io.InputStream

interface ArchiveReader : Closeable {
    fun listImageEntries(): List<ArchiveEntryRef>

    fun openEntryStream(entry: ArchiveEntryRef): InputStream
}
