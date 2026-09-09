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

    /** Data. One recorded screen, ready to show. */
    data class Capture(
        val name: String,
        val atMillis: Long,
        val body: String,
        val imagePath: String?,
    )

    /** Action. Newest first. Reads only the small text files; images stay on disk. */
    fun list(): List<Capture> {
        val texts = dir.listFiles { file -> file.name.endsWith(".txt") } ?: return emptyList()
        return texts
            .sortedByDescending { it.name }
            .map { file ->
                val name = file.nameWithoutExtension
                val raw = runCatching { file.readText() }.getOrDefault("")
                val image = File(dir, "$name.jpg").takeIf { it.exists() }
                Capture(
                    name = name,
                    atMillis = CaptureText.millisOf(raw) ?: file.lastModified(),
                    body = CaptureText.bodyOf(raw),
                    imagePath = image?.absolutePath,
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
     * An offer screen must survive a long shift, an idle home screen need not.
     * Money-bearing captures get their own, much larger quota.
     */
    private fun prune() {
        if (writesSincePrune++ < PRUNE_EVERY) return
        writesSincePrune = 0
        val texts = dir.listFiles { file -> file.name.endsWith(".txt") }
            ?.sortedByDescending { it.name } ?: return
        val (money, plain) = texts.partition { file ->
            runCatching { CaptureText.hasMoney(file.readText()) }.getOrDefault(false)
        }
        (money.drop(MONEY_CAPACITY) + plain.drop(PLAIN_CAPACITY)).forEach { text ->
            text.delete()
            File(dir, text.nameWithoutExtension + ".jpg").delete()
        }
    }

    private var writesSincePrune = 0

    private companion object {
        const val MONEY_CAPACITY = 400
        const val PLAIN_CAPACITY = 60
        const val PRUNE_EVERY = 20
        const val JPEG_QUALITY = 70
        val STAMP = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
    }
}
