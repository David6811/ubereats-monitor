package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Action. A plain log of the two signals that might say, better than the screens
 * do, that an offer was accepted and which job is being driven.
 *
 * The pickup screen is what proves acceptance today, and the board shows it was
 * missed on 16 of 71 taken jobs - nearly one in four - because it is only read
 * while Uber's window is on screen and readable. Two other channels might not
 * have that limit:
 *
 *  - Uber's own ongoing notification, which arrives with the screen off and
 *    says "You are currently online." between trips.
 *  - The click on Accept, which the accessibility service already receives and
 *    throws away.
 *
 * Neither is worth building on until a shift's worth of them has been read, so
 * this writes them down and changes nothing else.
 */
object TripProbe {

    private const val FILE_NAME = "trip-probe.log"
    private const val MAX_BYTES = 512 * 1024
    private val CLOCK = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    /** Repeats are the point of an ongoing notification and the noise of this log. */
    @Volatile
    private var lastLine: String? = null

    fun note(context: Context, channel: String, what: String) {
        val text = what.trim().replace('\n', ' ')
        if (text.isEmpty()) return
        val line = channel + "  " + text
        if (line == lastLine) return
        lastLine = line
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (file.length() > MAX_BYTES) file.delete()
            file.appendText(CLOCK.format(Date()) + "  " + line + "\n")
        }.onFailure { Log.w(TAG, "probe: could not write", it) }
    }

    private const val TAG = "UEatsMonitor"
}
