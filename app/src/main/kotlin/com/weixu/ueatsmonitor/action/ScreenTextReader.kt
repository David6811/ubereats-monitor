package com.weixu.ueatsmonitor.action

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

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

    /** Lines top to bottom, the same shape the accessibility path produces. */
    fun read(screen: Bitmap, onLines: (List<String>, Long) -> Unit) {
        val startedAt = System.currentTimeMillis()
        runCatching {
            recognizer.process(InputImage.fromBitmap(screen, 0))
                .addOnSuccessListener { result ->
                    val lines = result.textBlocks
                        .flatMap { block -> block.lines }
                        .sortedBy { line -> line.boundingBox?.top ?: 0 }
                        .map { line -> line.text.trim() }
                        .filter { it.isNotEmpty() }
                    onLines(lines, System.currentTimeMillis() - startedAt)
                }
                .addOnFailureListener { onLines(emptyList(), System.currentTimeMillis() - startedAt) }
        }.onFailure { onLines(emptyList(), System.currentTimeMillis() - startedAt) }
    }
}
