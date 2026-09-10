package com.weixu.ueatsmonitor.action

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Action. Reads the words off a screenshot, on the device, offline.
 *
 * The accessibility tree kept handing back a stale snapshot: at the moment an
 * offer card was on screen it still described the map underneath. The screenshot
 * never lied - so the picture, not the tree, is what gets read.
 */
object ScreenTextReader {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Results come back here, never on the caller's main looper. */
    private val callbacks = Executors.newSingleThreadExecutor()

    /**
     * Lines top to bottom, the same shape the accessibility path produces.
     *
     * Read at half size: everything that matters on an offer card is set large,
     * and quartering the pixels quarters the work on a phone that is doing this
     * every two seconds for hours.
     */
    fun read(screen: Bitmap, onLines: (List<String>, Long) -> Unit) {
        val startedAt = System.currentTimeMillis()
        val small = runCatching {
            Bitmap.createScaledBitmap(screen, screen.width / 2, screen.height / 2, true)
        }.getOrDefault(screen)
        runCatching {
            recognizer.process(InputImage.fromBitmap(small, 0))
                .addOnSuccessListener(callbacks) { result ->
                    val lines = rows(
                        result.textBlocks.flatMap { block -> block.lines }
                    )
                    if (small !== screen) runCatching { small.recycle() }
                    onLines(lines, System.currentTimeMillis() - startedAt)
                }
                .addOnFailureListener(callbacks) {
                    if (small !== screen) runCatching { small.recycle() }
                    onLines(emptyList(), System.currentTimeMillis() - startedAt)
                }
        }.onFailure { onLines(emptyList(), System.currentTimeMillis() - startedAt) }
    }

    /**
     * Rebuilds visual rows from the fragments the recogniser returns.
     *
     * ML Kit splits one line of the card across several blocks - "Guzman" in one,
     * "y Gomez (Springvale)" in another - and ordering the fragments by their top
     * edge alone scatters the halves. A real offer came back with a pickup of
     * "nan y Gomez (Springvale)" and a street of "elbeck Road" because of it.
     *
     * Fragments whose vertical centres fall within half a line height are the
     * same row, and within a row they read left to right.
     */
    private fun rows(fragments: List<com.google.mlkit.vision.text.Text.Line>): List<String> {
        data class Piece(val text: String, val middle: Int, val left: Int, val height: Int)

        val pieces = fragments.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            Piece(line.text.trim(), box.centerY(), box.left, box.height())
        }.filter { it.text.isNotEmpty() }.sortedBy { it.middle }

        val out = mutableListOf<String>()
        var group = mutableListOf<Piece>()

        fun flush() {
            if (group.isEmpty()) return
            out += group.sortedBy { it.left }.joinToString(" ") { it.text }
            group = mutableListOf()
        }

        for (piece in pieces) {
            val head = group.firstOrNull()
            val sameRow = head != null &&
                kotlin.math.abs(piece.middle - head.middle) <= (head.height / 2).coerceAtLeast(6)
            if (!sameRow) flush()
            group += piece
        }
        flush()
        return out
    }
}
