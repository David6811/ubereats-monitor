package com.weixu.ueatsmonitor.action

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Action. One flat line per judged screen, kept apart from the captures so a
 * whole shift can be read at a glance even after individual captures roll off.
 *
 * Appends. The first version rewrote the entire file on every capture, which by
 * the third hour of a shift meant rewriting megabytes every two seconds - the
 * kind of work that stalls the recorder at exactly the wrong moment.
 */
object DecisionLog {

    private const val FILE_NAME = "decisions.log"
    private const val MAX_LINES = 40_000
    private const val TRIM_EVERY = 2_000
    private val STAMP = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    private var writesSinceTrim = 0

    fun note(context: Context, atMillis: Long, source: String, decision: String) {
        val flat = decision.trim().replace('\n', ' ')
        val line = STAMP.format(Date(atMillis)) + "  " + source + "  " + flat + "\n"
        val file = File(context.filesDir, FILE_NAME)
        runCatching { file.appendText(line) }
        if (++writesSinceTrim >= TRIM_EVERY) {
            writesSinceTrim = 0
            trim(file)
        }
    }

    private fun trim(file: File) {
        runCatching {
            val lines = file.readLines()
            if (lines.size <= MAX_LINES) return
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
        }
    }
}
