package com.weixu.ueatsmonitor.action

import android.content.Context
import android.graphics.Bitmap
import com.weixu.ueatsmonitor.domain.GeoPoint
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

    /** Data. One recorded screen, ready to show. */
    data class Capture(
        val name: String,
        val atMillis: Long,
        val body: String,
        val imagePath: String?,
        val recordedAt: GeoPoint?,
        val fixAgeMillis: Long?,
    )

    /**
     * Action. Newest first, capped. A shift leaves tens of thousands of captures
     * and the review screen only ever shows the recent end of them; reading every
     * one to draw a list froze the screen.
     */
    fun list(limit: Int = PAGE): List<Capture> {
        val texts = dir.listFiles { file -> file.name.endsWith(".txt") } ?: return emptyList()
        return texts
            .sortedByDescending { it.name }
            .take(limit)
            .map { file ->
                val name = file.nameWithoutExtension
                val raw = runCatching { file.readText() }.getOrDefault("")
                val image = File(dir, "$name.jpg").takeIf { it.exists() }
                Capture(
                    name = name,
                    atMillis = CaptureText.millisOf(raw) ?: file.lastModified(),
                    body = CaptureText.bodyOf(raw),
                    imagePath = image?.absolutePath,
                    recordedAt = CaptureText.positionOf(raw),
                    fixAgeMillis = CaptureText.fixMillisOf(raw)?.let { fixMillis ->
                        (CaptureText.millisOf(raw) ?: file.lastModified()) - fixMillis
                    },
                )
            }
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

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

    /**
     * Until one real offer card has been seen, nothing on screen can be called
     * uninteresting - so keep everything and only cap the total.
     */
    private fun prune() {
        // Counting in memory, and only walking the directory once the count says
        // there is something to delete: listing ten thousand files every twenty
        // writes cost more than everything else this service does.
        if (known < 0) known = dir.list()?.size ?: 0
        known += 2
        if (known < CAPACITY * 2 + SLACK) return

        val texts = dir.listFiles { file -> file.name.endsWith(".txt") }
            ?.sortedByDescending { it.name } ?: return
        texts.drop(CAPACITY).forEach { text ->
            text.delete()
            File(dir, text.nameWithoutExtension + ".jpg").delete()
        }
        known = dir.list()?.size ?: 0
    }

    private var known = -1

    private companion object {
        /** About eleven hours at the two-second cadence; roughly 4 GB. */
        const val CAPACITY = 20_000
        const val SLACK = 400
        const val PAGE = 400
        const val JPEG_QUALITY = 70
        val STAMP = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
    }
}
