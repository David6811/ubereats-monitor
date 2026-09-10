package com.weixu.ueatsmonitor.action

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Action. One short line per frame: how much of the Accept band was button green,
 * and what was done about it.
 *
 * The threshold that decides whether a frame is worth reading was set from three
 * screenshots. This records the real distribution across a whole shift, so it can
 * be set from thousands of frames instead - including the ones at the moment an
 * offer was actually on screen.
 */
object BandLog {

    private const val FILE_NAME = "band.log"
    private const val MAX_LINES = 60_000
    private const val TRIM_EVERY = 5_000
    private val STAMP = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var writesSinceTrim = 0

    fun note(context: Context, atMillis: Long, green: Double, action: String) {
        val line = STAMP.format(Date(atMillis)) + " " +
            String.format(Locale.US, "%.3f", green) + " " + action + "\n"
        val file = File(context.filesDir, FILE_NAME)
        runCatching { file.appendText(line) }
        if (++writesSinceTrim >= TRIM_EVERY) {
            writesSinceTrim = 0
            runCatching {
                val lines = file.readLines()
                if (lines.size > MAX_LINES) {
                    file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
                }
            }
        }
    }
}
