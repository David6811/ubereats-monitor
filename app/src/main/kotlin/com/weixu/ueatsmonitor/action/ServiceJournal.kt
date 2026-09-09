package com.weixu.ueatsmonitor.action

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Action. Appends one line whenever the screen recorder connects or is torn down.
 * Without this, a shift that recorded nothing has no explanation.
 */
object ServiceJournal {

    private const val FILE_NAME = "service-journal.txt"
    private const val MAX_LINES = 200
    private val STAMP = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun note(context: Context, event: String) {
        val file = File(context.filesDir, FILE_NAME)
        runCatching {
            val kept = if (file.exists()) file.readLines().takeLast(MAX_LINES - 1) else emptyList()
            file.writeText((kept + (STAMP.format(Date()) + "  " + event)).joinToString("\n"))
        }
    }

    fun read(context: Context): List<String> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching { file.readLines().reversed() }.getOrDefault(emptyList())
    }
}
