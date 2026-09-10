package com.weixu.ueatsmonitor.action

import android.content.Context
import android.graphics.Bitmap
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.OfferRecord
import com.weixu.ueatsmonitor.domain.OfferRecordReader
import com.weixu.ueatsmonitor.domain.OfferParser
import com.weixu.ueatsmonitor.domain.OfferRun
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
        /** The app the frame came from; test mode records others. */
        val packageName: String?,
        /** The offer on that frame, or null - which is almost every frame. */
        val offer: OfferRecord?,
    )

    /**
     * The offers, newest first - read through the index rather than by walking the
     * frames, which are two orders of magnitude more numerous.
     */
    fun listOffers(limit: Int = PAGE): List<Capture> {
        val index = File(dir, INDEX)
        if (!index.exists()) buildIndex(index)
        val names = runCatching { index.readLines() }.getOrDefault(emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val frames = names.asReversed()
            .distinct()
            .asSequence()
            .map { File(dir, "$it.txt") }
            .filter { it.exists() }
            // Read enough frames to survive collapsing: one offer can hold the
            // screen for a minute, which is thirty frames of the same card.
            .take(limit * FRAMES_PER_OFFER)
            .map(::read)
            .filter { it.packageName?.let(OfferParser::isUberPackage) != false }
            .toList()
        return OfferRun.collapse(frames, Capture::atMillis, Capture::offer).take(limit)
    }

    private fun read(file: File): Capture {
        val name = file.nameWithoutExtension
        val raw = runCatching { file.readText() }.getOrDefault("")
        val image = File(dir, "$name.jpg").takeIf { it.exists() }
        val at = CaptureText.millisOf(raw) ?: file.lastModified()
        return Capture(
            name = name,
            atMillis = at,
            body = CaptureText.bodyOf(raw),
            imagePath = image?.absolutePath,
            recordedAt = CaptureText.positionOf(raw),
            fixAgeMillis = CaptureText.fixMillisOf(raw)?.let { at - it },
            packageName = CaptureText.packageOf(raw),
            offer = OfferRecordReader.read(raw),
        )
    }

    /** Once, for the frames that were already on disk before the index existed. */
    private fun buildIndex(index: File) {
        val texts = dir.listFiles { file -> file.name.endsWith(".txt") } ?: return
        val offers = texts.sortedBy { it.name }.filter { file ->
            runCatching { OfferRecordReader.read(file.readText()) != null }.getOrDefault(false)
        }
        runCatching {
            index.writeText(offers.joinToString("\n", postfix = "\n") { it.nameWithoutExtension })
        }
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun write(atMillis: Long, screen: Bitmap?, text: String): File {
        val stamp = STAMP.format(Date(atMillis))
        File(dir, "$stamp.txt").writeText(text)
        // A shift leaves twenty thousand frames and a handful of offers. Noting the
        // offers as they happen is what lets the review screen show all of a shift's
        // offers instead of whatever fell inside the last page of frames.
        if (OfferRecordReader.read(text) != null) {
            runCatching { File(dir, INDEX).appendText(stamp + "\n") }
        }
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
        const val INDEX = "offers.idx"

        /** A card holds the screen for about a minute at the two-second cadence. */
        const val FRAMES_PER_OFFER = 40
        const val JPEG_QUALITY = 70
        val STAMP = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
    }
}
