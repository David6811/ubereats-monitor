package com.weixu.ueatsmonitor.action

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Action. Writes one screenshot plus one text dump per capture into the app's
 * external files dir, so `adb pull` can fetch them without root.
 */
class CaptureStore(context: Context) {

    private val dir: File =
        File(context.getExternalFilesDir(null), "captures").apply { mkdirs() }

    fun write(atMillis: Long, screen: Bitmap?, text: String): File {
        val stamp = STAMP.format(Date(atMillis))
        File(dir, "$stamp.txt").writeText(text)
        screen?.let { bitmap ->
            File(dir, "$stamp.jpg").outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        }
        prune()
        return dir
    }

    /** Keep the newest [CAPACITY] files so a long shift cannot fill the phone. */
    private fun prune() {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(CAPACITY).forEach { it.delete() }
    }

    private companion object {
        const val CAPACITY = 800
        const val JPEG_QUALITY = 70
        val STAMP = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
    }
}
