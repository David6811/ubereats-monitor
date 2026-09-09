package com.weixu.ueatsmonitor.action

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Action. Owns the folder of screen-recording segments: where the next one goes,
 * how much room they take, and which of the oldest to drop when over budget.
 */
class RecordingStore(context: Context) {

    private val dir: File =
        File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }

    val folder: String get() = dir.absolutePath

    fun nextSegment(): File = File(dir, STAMP.format(Date()) + ".mp4")

    fun segments(): List<File> =
        dir.listFiles { file -> file.name.endsWith(".mp4") }?.sortedBy { it.name } ?: emptyList()

    fun totalBytes(): Long = segments().sumOf { it.length() }

    fun deleteAll(): Int {
        val files = segments()
        files.forEach { it.delete() }
        return files.size
    }

    /** Drops whole segments, oldest first, until the folder fits [budgetBytes]. */
    fun pruneTo(budgetBytes: Long) {
        val files = segments().toMutableList()
        var total = files.sumOf { it.length() }
        while (total > budgetBytes && files.size > 1) {
            val oldest = files.removeAt(0)
            total -= oldest.length()
            oldest.delete()
        }
    }

    private companion object {
        val STAMP = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
