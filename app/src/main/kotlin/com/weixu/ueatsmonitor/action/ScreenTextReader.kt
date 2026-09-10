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
                    val lines = result.textBlocks
                        .flatMap { block -> block.lines }
                        .sortedBy { line -> line.boundingBox?.top ?: 0 }
                        .map { line -> line.text.trim() }
                        .filter { it.isNotEmpty() }
                    if (small !== screen) runCatching { small.recycle() }
                    onLines(lines, System.currentTimeMillis() - startedAt)
                }
                .addOnFailureListener(callbacks) {
                    if (small !== screen) runCatching { small.recycle() }
                    onLines(emptyList(), System.currentTimeMillis() - startedAt)
                }
        }.onFailure { onLines(emptyList(), System.currentTimeMillis() - startedAt) }
    }
}
