package com.weixu.ueatsmonitor.action

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Action. One flat line per judged screen, kept apart from the captures so a
 * whole shift can be read at a glance even after individual captures roll off.
 */
object DecisionLog {

    private const val FILE_NAME = "decisions.log"
    private const val MAX_LINES = 4000
    private val STAMP = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun note(context: Context, atMillis: Long, packageName: String, decision: String) {
        val flat = decision.trim().replace('\n', ' ')
        val line = STAMP.format(Date(atMillis)) + "  " + packageName + "  " + flat
        val file = File(context.filesDir, FILE_NAME)
        runCatching {
            val kept = if (file.exists()) file.readLines().takeLast(MAX_LINES - 1) else emptyList()
            file.writeText((kept + line).joinToString("\n"))
        }
    }
}
